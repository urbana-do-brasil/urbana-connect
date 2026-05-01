package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.AssembledContext;
import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationContext;
import br.com.urbana.connect.domain.conversation.model.ConversationMessage;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageType;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.model.HumanHandoffRequest;
import br.com.urbana.connect.domain.conversation.port.out.AiGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.domain.conversation.port.out.HumanHandoffGateway;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ConversationFlowService {

    private static final String NO_TEXT_FALLBACK = "sem texto";
    private static final String TERMS_ACCEPTED_VALUE = "accepted";
    private static final String PAYMENT_METHOD_CARD = "CARTÃO";

    private static final Logger log = LoggerFactory.getLogger(ConversationFlowService.class);
    private static final Pattern HUMAN_HANDOFF_PATTERN = Pattern.compile(
        "(\\bhumano\\b|falar com algu[eé]m|atendimento humano|atendente|pessoa real)"
    );
    private static final Pattern TERMS_POSITIVE_PATTERN = Pattern.compile(
        "\\b(aceito|sim[, ]+aceito|ok[, ]+aceito|eu aceito|aceito sim)\\b"
    );
    private static final Pattern TERMS_NEGATIVE_PATTERN = Pattern.compile(
        "\\b(n[aã]o aceito|n[aã]o li ainda|n[aã]o concordo|tenho d[uú]vidas)\\b"
    );
    private static final Pattern AFFIRMATION_PATTERN = Pattern.compile(
        "\\b(sim|isso|confirmo|pode ser|perfeito|fechou|beleza)\\b"
    );
    private static final Pattern NEGATION_PATTERN = Pattern.compile(
        "\\b(n[aã]o|negativo|n[aã]o era isso|n[aã]o faz sentido|quero outro)\\b"
    );
    private static final List<String> SUPPORTED_PAYMENT_METHODS = List.of("PIX", PAYMENT_METHOD_CARD);

    private final ConversationLifecycleService conversationLifecycleService;
    private final ConversationGateway conversationGateway;
    private final ConversationMessageGateway conversationMessageGateway;
    private final ServiceCatalogGateway serviceCatalogGateway;
    private final WhatsAppMessageGateway whatsAppMessageGateway;
    private final AiGateway aiGateway;
    private final HumanHandoffGateway humanHandoffGateway;
    private final StepContractRegistry stepContractRegistry;
    private final ConversationContextAssembler contextAssembler;
    private final ConversationResponseValidator responseValidator;
    private final ConversationPolicyEngine policyEngine;
    private final ConversationActionExecutor actionExecutor;

    public ConversationFlowService(
            ConversationLifecycleService conversationLifecycleService,
            ConversationGateway conversationGateway,
            ConversationMessageGateway conversationMessageGateway,
            ServiceCatalogGateway serviceCatalogGateway,
            WhatsAppMessageGateway whatsAppMessageGateway,
            AiGateway aiGateway,
            HumanHandoffGateway humanHandoffGateway,
            StepContractRegistry stepContractRegistry,
            ConversationContextAssembler contextAssembler,
            ConversationResponseValidator responseValidator,
            ConversationPolicyEngine policyEngine,
            ConversationActionExecutor actionExecutor) {
        this.conversationLifecycleService = conversationLifecycleService;
        this.conversationGateway = conversationGateway;
        this.conversationMessageGateway = conversationMessageGateway;
        this.serviceCatalogGateway = serviceCatalogGateway;
        this.whatsAppMessageGateway = whatsAppMessageGateway;
        this.aiGateway = aiGateway;
        this.humanHandoffGateway = humanHandoffGateway;
        this.stepContractRegistry = stepContractRegistry;
        this.contextAssembler = contextAssembler;
        this.responseValidator = responseValidator;
        this.policyEngine = policyEngine;
        this.actionExecutor = actionExecutor;
    }

    public Conversation handleIncomingMessage(InboundWhatsAppMessage inboundMessage, Instant receivedAt) {
        if (isDuplicateInboundMessage(inboundMessage)) {
            return conversationGateway.findLatestByPhoneNumber(inboundMessage.phoneNumber())
                .orElseGet(() -> conversationLifecycleService.resumeOrStart(inboundMessage.phoneNumber(), receivedAt));
        }

        Conversation conversation = conversationLifecycleService.resumeOrStart(inboundMessage.phoneNumber(), receivedAt);
        List<ServiceCatalogItem> availableServices = serviceCatalogGateway.findAvailable();

        logIncomingMessage(conversation, inboundMessage);

        if (!persistInboundMessage(conversation, inboundMessage, receivedAt)) {
            return conversation;
        }

        if (isHumanHandoffRequested(inboundMessage.textBody())) {
            handleHumanHandoff(conversation, inboundMessage, receivedAt);
            return conversation;
        }

        Conversation normalizedConversation = migrateLegacyDiscoveryStepIfNeeded(conversation, inboundMessage.phoneNumber(), receivedAt);

        return switch (normalizedConversation.currentStep()) {
            case GREETING -> handleGreeting(normalizedConversation, inboundMessage, availableServices, receivedAt);
            case ICP_QUALIFICATION -> handleIcpQualification(normalizedConversation, inboundMessage, availableServices, receivedAt);
            case SERVICE_DISCOVERY, TRIAGE_GUIDED, TRIAGE_DIRECT ->
                handleServiceDiscovery(normalizedConversation, inboundMessage, availableServices, receivedAt);
            case AWAITING_CONFIRMATION -> handleAwaitingConfirmation(normalizedConversation, inboundMessage, availableServices, receivedAt);
            case AWAITING_TERMS -> handleAwaitingTerms(normalizedConversation, inboundMessage, receivedAt);
            case AWAITING_PAYMENT_METHOD -> handleAwaitingPaymentMethod(normalizedConversation, inboundMessage, availableServices, receivedAt);
            default -> normalizedConversation;
        };
    }

    private Conversation handleGreeting(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        if (isFirstTouch(conversation, receivedAt)) {
            sendSafely(
                inboundMessage.phoneNumber(),
                conversation.currentStep(),
                () -> whatsAppMessageGateway.sendGreeting(inboundMessage.phoneNumber())
            );
            return conversation;
        }

        if ("YES_HELP".equals(inboundMessage.interactiveReplyId())) {
            return advanceGreetingDeterministically(conversation, inboundMessage.phoneNumber(), true, receivedAt, "greeting_yes_help");
        }

        if ("NO_HELP".equals(inboundMessage.interactiveReplyId())) {
            return advanceGreetingDeterministically(conversation, inboundMessage.phoneNumber(), false, receivedAt, "greeting_no_help");
        }

        if (!hasText(inboundMessage)) {
            executeFallback(conversation, inboundMessage.phoneNumber(), availableServices);
            return conversation;
        }

        return handleConversationalStep(conversation, inboundMessage, availableServices, receivedAt);
    }

    private Conversation handleIcpQualification(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        if (!hasText(inboundMessage)) {
            executeFallback(conversation, inboundMessage.phoneNumber(), availableServices);
            return conversation;
        }

        return handleConversationalStep(conversation, inboundMessage, availableServices, receivedAt);
    }

    private Conversation handleServiceDiscovery(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        Optional<ServiceCatalogItem> selectedService = resolveSelectedService(availableServices, inboundMessage.interactiveReplyId());
        if (selectedService.isPresent()) {
            return moveToConfirmation(conversation, inboundMessage.phoneNumber(), selectedService.get(), receivedAt, "service_selected_interactive");
        }

        if (!hasText(inboundMessage)) {
            executeFallback(conversation, inboundMessage.phoneNumber(), availableServices);
            return conversation;
        }

        return handleConversationalStep(conversation, inboundMessage, availableServices, receivedAt);
    }

    private Conversation handleAwaitingConfirmation(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        String replyId = inboundMessage.interactiveReplyId();

        if (hasText(inboundMessage)) {
            if (isAffirmation(inboundMessage.textBody())) {
                Conversation updated = saveTransition(
                    conversation,
                    confirmSelectedService(conversation, receivedAt).moveTo(ConversationStep.AWAITING_TERMS, receivedAt),
                    inboundMessage.phoneNumber(),
                    "confirmation_ai_affirmation"
                );
                sendSafely(
                    inboundMessage.phoneNumber(),
                    updated.currentStep(),
                    () -> whatsAppMessageGateway.sendTermsOfUse(inboundMessage.phoneNumber())
                );
                return updated;
            }

            if (isNegation(inboundMessage.textBody())) {
                Conversation updated = saveTransition(
                    conversation,
                    resetServiceDiscoveryState(conversation, receivedAt).moveTo(ConversationStep.SERVICE_DISCOVERY, receivedAt),
                    inboundMessage.phoneNumber(),
                    "confirmation_ai_negation"
                );
                sendSafely(
                    inboundMessage.phoneNumber(),
                    updated.currentStep(),
                    () -> whatsAppMessageGateway.sendTextMessage(inboundMessage.phoneNumber(), defaultServiceDiscoveryPrompt(updated))
                );
                sendSafely(
                    inboundMessage.phoneNumber(),
                    updated.currentStep(),
                    () -> sendStructuredDiscoveryOptions(inboundMessage.phoneNumber(), updated, availableServices)
                );
                return updated;
            }
        }

        if ("CONFIRM_SERVICE".equals(replyId)) {
            Conversation updated = saveTransition(
                conversation,
                confirmSelectedService(conversation, receivedAt).moveTo(ConversationStep.AWAITING_TERMS, receivedAt),
                inboundMessage.phoneNumber(),
                "confirmation_button"
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendTermsOfUse(inboundMessage.phoneNumber())
            );
            return updated;
        }

        if ("RESELECT_SERVICE".equals(replyId)) {
            Conversation updated = saveTransition(
                conversation,
                resetServiceDiscoveryState(conversation, receivedAt).moveTo(ConversationStep.SERVICE_DISCOVERY, receivedAt),
                inboundMessage.phoneNumber(),
                "confirmation_reselect"
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendTextMessage(inboundMessage.phoneNumber(), defaultServiceDiscoveryPrompt(updated))
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> sendStructuredDiscoveryOptions(inboundMessage.phoneNumber(), updated, availableServices)
            );
            return updated;
        }

        return serviceCatalogGateway.findByType(conversation.selectedService())
            .map(service -> {
                executeFallback(conversation, inboundMessage.phoneNumber(), availableServices);
                return conversation;
            })
            .orElse(conversation);
    }

    private Conversation handleAwaitingTerms(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            Instant receivedAt) {
        if ("TERMS_ACCEPT".equals(inboundMessage.interactiveReplyId())) {
            Conversation updated = saveTransition(
                conversation,
                applySlotUpdates(conversation, List.of(
                    new ConversationSlotUpdate(
                        ConversationSlotName.TERMS_ACCEPTED,
                        TERMS_ACCEPTED_VALUE,
                        ConversationSlotLevel.CONFIRMED,
                        1.0,
                        ConversationSlotSource.EXPLICIT
                    )
                ), receivedAt).moveTo(ConversationStep.AWAITING_PAYMENT_METHOD, receivedAt),
                inboundMessage.phoneNumber(),
                "terms_accepted_button"
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendPaymentMethodOptions(inboundMessage.phoneNumber())
            );
            return updated;
        }

        if ("TERMS_DECLINE".equals(inboundMessage.interactiveReplyId())) {
            executeFallback(conversation, inboundMessage.phoneNumber(), List.of());
            return conversation;
        }

        if (containsExplicitTermsDecline(inboundMessage.textBody())) {
            executeFallback(conversation, inboundMessage.phoneNumber(), List.of());
            return conversation;
        }

        if (containsTermsAcceptance(inboundMessage.textBody())) {
            Conversation updated = saveTransition(
                conversation,
                applySlotUpdates(conversation, List.of(
                    new ConversationSlotUpdate(
                        ConversationSlotName.TERMS_ACCEPTED,
                        TERMS_ACCEPTED_VALUE,
                        ConversationSlotLevel.CONFIRMED,
                        1.0,
                        ConversationSlotSource.EXPLICIT
                    )
                ), receivedAt).moveTo(ConversationStep.AWAITING_PAYMENT_METHOD, receivedAt),
                inboundMessage.phoneNumber(),
                "terms_accepted"
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendPaymentMethodOptions(inboundMessage.phoneNumber())
            );
            return updated;
        }

        executeFallback(conversation, inboundMessage.phoneNumber(), List.of());
        return conversation;
    }

    private Conversation handleAwaitingPaymentMethod(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        String paymentMethod = resolvePaymentMethod(inboundMessage.interactiveReplyId());
        if (paymentMethod == null) {
            paymentMethod = resolvePaymentMethodFromText(inboundMessage.textBody());
        }
        if (paymentMethod != null) {
            Optional<ServiceCatalogItem> selectedService = serviceCatalogGateway.findByType(conversation.selectedService());
            if (selectedService.isPresent()) {
                Conversation updated = saveTransition(
                    conversation,
                    applySlotUpdates(conversation, List.of(
                        new ConversationSlotUpdate(
                            ConversationSlotName.PAYMENT_METHOD,
                            paymentMethod,
                            ConversationSlotLevel.CONFIRMED,
                            1.0,
                            ConversationSlotSource.EXPLICIT
                        )
                    ), receivedAt).selectPaymentMethod(paymentMethod, ConversationStep.PAYMENT_LINK_SENT, receivedAt),
                    inboundMessage.phoneNumber(),
                    "payment_method_selected_" + paymentMethod.toLowerCase(Locale.ROOT)
                );
                sendSafely(
                    inboundMessage.phoneNumber(),
                    updated.currentStep(),
                    () -> whatsAppMessageGateway.sendPaymentLink(inboundMessage.phoneNumber(), selectedService.get())
                );
                sendSafely(
                    inboundMessage.phoneNumber(),
                    updated.currentStep(),
                    () -> whatsAppMessageGateway.sendClosingMessage(inboundMessage.phoneNumber())
                );
                return updated;
            }

            if (log.isErrorEnabled()) {
                log.error(
                    "Servico {} nao encontrado para enviar link de pagamento para {}",
                    conversation.selectedService(),
                    maskPhoneNumber(inboundMessage.phoneNumber())
                );
            }
            Conversation updated = saveTransition(
                conversation,
                resetServiceDiscoveryState(conversation, receivedAt).moveTo(ConversationStep.SERVICE_DISCOVERY, receivedAt),
                inboundMessage.phoneNumber(),
                "payment_service_missing"
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendTextMessage(inboundMessage.phoneNumber(), defaultServiceDiscoveryPrompt(updated))
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> sendStructuredDiscoveryOptions(inboundMessage.phoneNumber(), updated, availableServices)
            );
            return updated;
        }

        executeFallback(conversation, inboundMessage.phoneNumber(), availableServices);
        return conversation;
    }

    private Conversation advanceGreetingDeterministically(
            Conversation conversation,
            String phoneNumber,
            boolean needsHelp,
            Instant receivedAt,
            String reason) {
        Conversation updated = saveTransition(
            conversation,
            applySlotUpdates(conversation, List.of(
                new ConversationSlotUpdate(
                    ConversationSlotName.NEEDS_DISCOVERY_HELP,
                    Boolean.toString(needsHelp),
                    ConversationSlotLevel.CONFIRMED,
                    1.0,
                    ConversationSlotSource.EXPLICIT
                )
            ), receivedAt).moveTo(ConversationStep.ICP_QUALIFICATION, receivedAt),
            phoneNumber,
            reason
        );
        sendSafely(
            phoneNumber,
            updated.currentStep(),
            () -> whatsAppMessageGateway.sendTextMessage(phoneNumber, defaultIcpPrompt(updated))
        );
        return updated;
    }

    private Conversation migrateLegacyDiscoveryStepIfNeeded(Conversation conversation, String phoneNumber, Instant receivedAt) {
        if (conversation.currentStep() == ConversationStep.TRIAGE_GUIDED) {
            Conversation migrated = applySlotUpdates(conversation, List.of(
                new ConversationSlotUpdate(
                    ConversationSlotName.NEEDS_DISCOVERY_HELP,
                    Boolean.TRUE.toString(),
                    ConversationSlotLevel.CONFIRMED,
                    1.0,
                    ConversationSlotSource.EXPLICIT
                )
            ), receivedAt).moveTo(ConversationStep.SERVICE_DISCOVERY, receivedAt);
            return saveTransition(conversation, migrated, phoneNumber, "legacy_guided_to_service_discovery");
        }
        if (conversation.currentStep() == ConversationStep.TRIAGE_DIRECT) {
            Conversation migrated = applySlotUpdates(conversation, List.of(
                new ConversationSlotUpdate(
                    ConversationSlotName.NEEDS_DISCOVERY_HELP,
                    Boolean.FALSE.toString(),
                    ConversationSlotLevel.CONFIRMED,
                    1.0,
                    ConversationSlotSource.EXPLICIT
                )
            ), receivedAt).moveTo(ConversationStep.SERVICE_DISCOVERY, receivedAt);
            return saveTransition(conversation, migrated, phoneNumber, "legacy_direct_to_service_discovery");
        }
        return conversation;
    }

    private Conversation moveToConfirmation(
            Conversation conversation,
            String phoneNumber,
            ServiceCatalogItem selectedService,
            Instant receivedAt,
            String reason) {
        Conversation updated = saveTransition(
            conversation,
            applySlotUpdates(conversation, List.of(
                new ConversationSlotUpdate(
                    ConversationSlotName.SUGGESTED_SERVICE,
                    selectedService.type().name(),
                    ConversationSlotLevel.TENTATIVE,
                    1.0,
                    ConversationSlotSource.EXPLICIT
                )
            ), receivedAt).selectService(selectedService.type(), ConversationStep.AWAITING_CONFIRMATION, receivedAt),
            phoneNumber,
            reason
        );

        sendSafely(
            phoneNumber,
            updated.currentStep(),
            () -> whatsAppMessageGateway.sendServicePresentation(phoneNumber, selectedService)
        );
        return updated;
    }

    private Conversation confirmSelectedService(Conversation conversation, Instant receivedAt) {
        if (conversation.selectedService() == null) {
            return conversation;
        }

        return applySlotUpdates(conversation, List.of(
            new ConversationSlotUpdate(
                ConversationSlotName.CONFIRMED_SERVICE,
                conversation.selectedService().name(),
                ConversationSlotLevel.CONFIRMED,
                1.0,
                ConversationSlotSource.EXPLICIT
            )
        ), receivedAt);
    }

    private Conversation resetServiceDiscoveryState(Conversation conversation, Instant receivedAt) {
        ConversationContext resetContext = conversation.context()
            .withoutSlot(ConversationSlotName.SUGGESTED_SERVICE)
            .withoutSlot(ConversationSlotName.CONFIRMED_SERVICE);
        return conversation
            .withContext(resetContext, receivedAt)
            .clearSelectedService(receivedAt);
    }

    private Conversation applySlotUpdates(
            Conversation conversation,
            List<ConversationSlotUpdate> slotUpdates,
            Instant receivedAt) {
        ConversationContext updatedContext = conversation.context();
        for (ConversationSlotUpdate slotUpdate : slotUpdates) {
            if (slotUpdate == null
                    || slotUpdate.slot() == null
                    || slotUpdate.value() == null
                    || slotUpdate.value().isBlank()
                    || !isValidSlotUpdate(slotUpdate)) {
                continue;
            }
            updatedContext = updatedContext.withSlot(slotUpdate.slot(), slotUpdate.toSlotValue());
            if (slotUpdate.slot() == ConversationSlotName.PAYMENT_METHOD
                    && slotUpdate.level() == ConversationSlotLevel.CONFIRMED) {
                updatedContext = updatedContext.withPaymentMethod(slotUpdate.value());
            }
        }
        return conversation.withContext(updatedContext, receivedAt);
    }

    private Optional<ServiceCatalogItem> resolveSelectedService(List<ServiceCatalogItem> availableServices, String replyId) {
        if (replyId == null || replyId.isBlank()) {
            return Optional.empty();
        }

        try {
            ServiceType selectedType = ServiceType.valueOf(replyId);
            return availableServices.stream()
                .filter(service -> service.type() == selectedType)
                .findFirst();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<ServiceCatalogItem> resolveSuggestedService(
            Conversation conversation,
            List<ServiceCatalogItem> availableServices) {
        return conversation.context().slotValue(ConversationSlotName.SUGGESTED_SERVICE)
            .flatMap(slotValue -> {
                try {
                    ServiceType selectedType = ServiceType.valueOf(slotValue);
                    return availableServices.stream()
                        .filter(service -> service.type() == selectedType)
                        .findFirst();
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            });
    }

    private void sendStructuredDiscoveryOptions(
            String phoneNumber,
            Conversation conversation,
            List<ServiceCatalogItem> availableServices) {
        if (needsDiscoveryHelp(conversation)) {
            whatsAppMessageGateway.sendGuidedTriageOptions(phoneNumber, availableServices);
            return;
        }
        whatsAppMessageGateway.sendDirectTriageOptions(phoneNumber, availableServices);
    }

    private boolean needsDiscoveryHelp(Conversation conversation) {
        return conversation.context().slotValue(ConversationSlotName.NEEDS_DISCOVERY_HELP)
            .map(Boolean::parseBoolean)
            .orElse(true);
    }

    private Conversation handleConversationalStep(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        StepContract stepContract = stepContractRegistry.findByStep(conversation.currentStep())
            .orElseThrow(() -> new IllegalStateException("StepContract ausente para " + conversation.currentStep()));

        if (policyEngine.shouldTriggerStructuredEscape(conversation, stepContract)) {
            logPolicyDecision(conversation, "structured_escape", "stagnation_limit");
            sendSafely(
                inboundMessage.phoneNumber(),
                conversation.currentStep(),
                () -> actionExecutor.sendStructuredEscape(
                    inboundMessage.phoneNumber(),
                    conversation,
                    stepContract.structuredEscapeType(),
                    availableServices
                )
            );
            Conversation reset = conversation.withContext(
                conversation.context().withTurnsWithoutProgress(conversation.currentStep(), 0),
                receivedAt
            );
            return saveTransition(conversation, reset, inboundMessage.phoneNumber(), "structured_escape");
        }

        AssembledContext assembledContext = contextAssembler.assemble(
            conversation,
            inboundMessage,
            availableServices,
            stepContract
        );
        logAssemblerLayers(conversation, assembledContext);

        ConversationalAiReply reply = Optional.ofNullable(aiGateway.converse(assembledContext))
            .orElse(ConversationalAiReply.fallback("null_reply"));
        Conversation updatedConversation = applySlotUpdates(conversation, reply.slotUpdates(), receivedAt);
        ResponseValidationResult validationResult = responseValidator.validate(
            stepContract,
            reply,
            availableServices
        );
        if (!validationResult.valid()) {
            logValidationFailure(conversation, validationResult.reason());
        }

        ConversationPolicyDecision decision = policyEngine.decide(
            conversation,
            stepContract,
            reply,
            validationResult,
            updatedConversation,
            receivedAt
        );
        logPolicyDecision(conversation, decision.type().name(), decision.reason());

        return switch (decision.type()) {
            case APPLY_FALLBACK -> {
                executeFallback(conversation, inboundMessage.phoneNumber(), availableServices);
                Conversation saved = saveTransition(conversation, decision.updatedConversation(), inboundMessage.phoneNumber(), decision.reason());
                yield saved;
            }
            case ACCEPT_REPLY -> {
                Conversation saved = saveTransition(conversation, decision.updatedConversation(), inboundMessage.phoneNumber(), decision.reason());
                sendSafely(
                    inboundMessage.phoneNumber(),
                    saved.currentStep(),
                    () -> actionExecutor.sendReply(inboundMessage.phoneNumber(), reply.replyText())
                );
                if (reply.shouldOfferStructuredOptions()) {
                    sendSafely(
                        inboundMessage.phoneNumber(),
                        saved.currentStep(),
                        () -> actionExecutor.sendStructuredEscape(
                            inboundMessage.phoneNumber(),
                            saved,
                            stepContract.structuredEscapeType(),
                            availableServices
                        )
                    );
                }
                yield saved;
            }
            case ACCEPT_AND_ADVANCE -> handleAcceptedAdvance(
                conversation,
                decision.updatedConversation(),
                inboundMessage.phoneNumber(),
                availableServices,
                reply,
                receivedAt
            );
            case TRIGGER_STRUCTURED_ESCAPE -> {
                sendSafely(
                    inboundMessage.phoneNumber(),
                    conversation.currentStep(),
                    () -> actionExecutor.sendStructuredEscape(
                        inboundMessage.phoneNumber(),
                        conversation,
                        stepContract.structuredEscapeType(),
                        availableServices
                    )
                );
                yield saveTransition(conversation, decision.updatedConversation(), inboundMessage.phoneNumber(), decision.reason());
            }
        };
    }

    private Conversation handleAcceptedAdvance(
            Conversation previousConversation,
            Conversation updatedConversation,
            String phoneNumber,
            List<ServiceCatalogItem> availableServices,
            ConversationalAiReply reply,
            Instant receivedAt) {
        if (updatedConversation.currentStep() == ConversationStep.GREETING && reply.suggestedNextStep() == ConversationStep.ICP_QUALIFICATION) {
            Conversation advanced = updatedConversation.moveTo(ConversationStep.ICP_QUALIFICATION, receivedAt);
            Conversation saved = saveTransition(previousConversation, advanced, phoneNumber, "greeting_ai_advance");
            sendSafely(phoneNumber, saved.currentStep(), () -> actionExecutor.sendReply(
                phoneNumber,
                usableReplyOrDefault(reply.replyText(), defaultIcpPrompt(saved))
            ));
            return saved;
        }

        if (updatedConversation.currentStep() == ConversationStep.ICP_QUALIFICATION && reply.suggestedNextStep() == ConversationStep.SERVICE_DISCOVERY) {
            Conversation advanced = updatedConversation.moveTo(ConversationStep.SERVICE_DISCOVERY, receivedAt);
            Conversation saved = saveTransition(previousConversation, advanced, phoneNumber, "icp_ai_advance");
            sendSafely(phoneNumber, saved.currentStep(), () -> actionExecutor.sendReply(
                phoneNumber,
                usableReplyOrDefault(reply.replyText(), defaultServiceDiscoveryPrompt(saved))
            ));
            return saved;
        }

        if ((updatedConversation.currentStep() == ConversationStep.SERVICE_DISCOVERY
                || updatedConversation.currentStep() == ConversationStep.TRIAGE_DIRECT
                || updatedConversation.currentStep() == ConversationStep.TRIAGE_GUIDED)
                && reply.suggestedNextStep() == ConversationStep.AWAITING_CONFIRMATION) {
            Optional<ServiceCatalogItem> suggestedService = resolveSuggestedService(updatedConversation, availableServices);
            if (suggestedService.isPresent()) {
                if (reply.replyText() != null && !reply.replyText().isBlank()) {
                    sendSafely(phoneNumber, updatedConversation.currentStep(), () -> actionExecutor.sendReply(phoneNumber, reply.replyText()));
                }
                return moveToConfirmation(updatedConversation, phoneNumber, suggestedService.get(), receivedAt, "service_discovery_ai_advance");
            }
        }

        return saveTransition(previousConversation, updatedConversation, phoneNumber, "advance_without_transition");
    }

    private String usableReplyOrDefault(String replyText, String fallbackText) {
        return replyText == null || replyText.isBlank() ? fallbackText : replyText;
    }

    private String defaultIcpPrompt(Conversation conversation) {
        if (needsDiscoveryHelp(conversation)) {
            return "Perfeito. Antes de te indicar o melhor caminho, quero te conhecer um pouquinho 😊 Como você prefere que eu te trate?";
        }
        return "Perfeito. Antes de seguirmos, quero te conhecer rapidinho para te atender melhor 😊 Como você prefere que eu te trate?";
    }

    private String defaultServiceDiscoveryPrompt(Conversation conversation) {
        if (needsDiscoveryHelp(conversation)) {
            return "Agora me conta um pouco melhor do que você está buscando, para eu te indicar a opção mais adequada da Urba.";
        }
        return "Perfeito. Me conta qual serviço você tem em mente ou descreve brevemente o que você quer resolver, que eu organizo isso com você.";
    }

    private void sendSafely(String phoneNumber, ConversationStep step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.error(
                "Falha ao enviar mensagem para {} na etapa {}: {}",
                maskPhoneNumber(phoneNumber),
                step,
                exception.getMessage()
            );
        }
    }

    private void executeFallback(Conversation conversation, String phoneNumber, List<ServiceCatalogItem> availableServices) {
        StepContract stepContract = stepContractRegistry.findByStep(conversation.currentStep())
            .orElseThrow(() -> new IllegalStateException("StepContract ausente para " + conversation.currentStep()));
        sendSafely(
            phoneNumber,
            conversation.currentStep(),
            () -> actionExecutor.executeFallback(
                phoneNumber,
                conversation,
                stepContract,
                availableServices,
                defaultIcpPrompt(conversation),
                defaultServiceDiscoveryPrompt(conversation)
            )
        );
    }

    private Conversation saveTransition(Conversation previous, Conversation next, String phoneNumber, String reason) {
        Conversation saved = conversationGateway.save(next);
        if (previous.currentStep() != saved.currentStep() && log.isInfoEnabled()) {
            log.info(
                "Transição de conversa: phoneNumber={} from={} to={} reason={}",
                maskPhoneNumber(phoneNumber),
                previous.currentStep(),
                saved.currentStep(),
                reason
            );
        }
        return saved;
    }

    private void handleHumanHandoff(Conversation conversation, InboundWhatsAppMessage inboundMessage, Instant receivedAt) {
        if (log.isInfoEnabled()) {
            log.info(
                "Solicitacao de handoff humano recebida para {} na etapa {}",
                maskPhoneNumber(inboundMessage.phoneNumber()),
                conversation.currentStep()
            );
        }
        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendHumanHandoffAcknowledgement(inboundMessage.phoneNumber())
        );

        try {
            humanHandoffGateway.notifyTeam(new HumanHandoffRequest(
                inboundMessage.phoneNumber(),
                conversation.currentStep(),
                conversation.selectedService(),
                conversation.context().paymentMethod(),
                buildRecentMessagesForHandoff(conversation.id()),
                receivedAt
            ));
        } catch (RuntimeException exception) {
            if (log.isErrorEnabled()) {
                log.error(
                    "Falha ao notificar handoff humano para {} na etapa {}: {}",
                    maskPhoneNumber(inboundMessage.phoneNumber()),
                    conversation.currentStep(),
                    exception.getMessage()
                );
            }
        }
    }

    private boolean containsTermsAcceptance(String textBody) {
        if (textBody == null || textBody.isBlank()) {
            return false;
        }

        String normalized = textBody.toLowerCase(Locale.ROOT);
        if (TERMS_NEGATIVE_PATTERN.matcher(normalized).find()) {
            return false;
        }

        return TERMS_POSITIVE_PATTERN.matcher(normalized).find();
    }

    private boolean containsExplicitTermsDecline(String textBody) {
        if (textBody == null || textBody.isBlank()) {
            return false;
        }

        return TERMS_NEGATIVE_PATTERN.matcher(textBody.toLowerCase(Locale.ROOT)).find();
    }

    private boolean isAffirmation(String textBody) {
        if (textBody == null || textBody.isBlank()) {
            return false;
        }
        return AFFIRMATION_PATTERN.matcher(textBody.toLowerCase(Locale.ROOT)).find()
            && !NEGATION_PATTERN.matcher(textBody.toLowerCase(Locale.ROOT)).find();
    }

    private boolean isNegation(String textBody) {
        if (textBody == null || textBody.isBlank()) {
            return false;
        }
        return NEGATION_PATTERN.matcher(textBody.toLowerCase(Locale.ROOT)).find();
    }

    private boolean isValidSlotUpdate(ConversationSlotUpdate slotUpdate) {
        return switch (slotUpdate.slot()) {
            case NEEDS_DISCOVERY_HELP -> "true".equalsIgnoreCase(slotUpdate.value())
                || "false".equalsIgnoreCase(slotUpdate.value());
            case SUGGESTED_SERVICE, CONFIRMED_SERVICE -> isKnownService(slotUpdate.value());
            case PAYMENT_METHOD -> SUPPORTED_PAYMENT_METHODS.contains(slotUpdate.value().toUpperCase(Locale.ROOT));
            case TERMS_ACCEPTED -> TERMS_ACCEPTED_VALUE.equalsIgnoreCase(slotUpdate.value());
            case PRONOUN_PREFERENCE, FIRST_TIME_HIRING_DESIGNER, OCCUPATION -> true;
        };
    }

    private boolean isKnownService(String value) {
        try {
            ServiceType.valueOf(value);
            return true;
        } catch (IllegalArgumentException exception) {
            if (log.isWarnEnabled()) {
                log.warn("Slot de serviço inválido descartado: {}", value);
            }
            return false;
        }
    }

    private boolean isHumanHandoffRequested(String textBody) {
        if (textBody == null || textBody.isBlank()) {
            return false;
        }

        String normalized = textBody.toLowerCase(Locale.ROOT);
        return HUMAN_HANDOFF_PATTERN.matcher(normalized).find();
    }

    private String resolvePaymentMethod(String replyId) {
        return switch (replyId) {
            case "PAYMENT_PIX" -> "PIX";
            case "PAYMENT_CARD" -> PAYMENT_METHOD_CARD;
            default -> null;
        };
    }

    private String resolvePaymentMethodFromText(String textBody) {
        if (textBody == null || textBody.isBlank()) {
            return null;
        }

        String normalized = textBody.toLowerCase(Locale.ROOT);
        if (normalized.contains("pix")) {
            return "PIX";
        }
        if (normalized.contains("cart")) {
            return PAYMENT_METHOD_CARD;
        }
        return null;
    }

    private String resolveMessageType(InboundWhatsAppMessage inboundMessage) {
        if (hasText(inboundMessage)) {
            return "text";
        }
        if (inboundMessage.interactiveReplyId() != null && !inboundMessage.interactiveReplyId().isBlank()) {
            return "interactive";
        }
        return "unknown";
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "***";
        }
        if (phoneNumber.length() <= 7) {
            return "***";
        }

        int prefixLength = Math.min(5, phoneNumber.length() - 4);
        return phoneNumber.substring(0, prefixLength) + "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    private void logAssemblerLayers(Conversation conversation, AssembledContext assembledContext) {
        if (log.isInfoEnabled()) {
            log.info(
                "Assembler de contexto: conversationId={} step={} layers={}",
                conversation.id(),
                conversation.currentStep(),
                assembledContext.includedLayers()
            );
        }
    }

    private void logValidationFailure(Conversation conversation, String reason) {
        if (log.isWarnEnabled()) {
            log.warn(
                "Resposta da IA rejeitada: conversationId={} step={} reason={}",
                conversation.id(),
                conversation.currentStep(),
                reason
            );
        }
    }

    private void logPolicyDecision(Conversation conversation, String decision, String reason) {
        if (log.isInfoEnabled()) {
            log.info(
                "Policy decision: conversationId={} step={} decision={} reason={} turnsWithoutProgress={}",
                conversation.id(),
                conversation.currentStep(),
                decision,
                reason,
                conversation.context().turnsWithoutProgress()
            );
        }
    }


    private boolean hasText(InboundWhatsAppMessage inboundMessage) {
        return inboundMessage.textBody() != null && !inboundMessage.textBody().isBlank();
    }

    private boolean isFirstTouch(Conversation conversation, Instant receivedAt) {
        return conversation.createdAt().equals(receivedAt)
            && conversation.updatedAt().equals(receivedAt)
            && conversation.selectedService() == null;
    }

    private boolean persistInboundMessage(Conversation conversation, InboundWhatsAppMessage inboundMessage, Instant receivedAt) {
        if (conversation.id() == null) {
            return true;
        }

        try {
            conversationMessageGateway.save(ConversationMessage.inbound(
                conversation.id(),
                inboundMessage.phoneNumber(),
                resolveInboundMessageType(inboundMessage),
                resolveInboundRawText(inboundMessage),
                blankToNull(inboundMessage.interactiveReplyId()),
                blankToNull(inboundMessage.providerMessageId()),
                receivedAt,
                conversation.currentStep().name()
            ));
            return true;
        } catch (DuplicateKeyException exception) {
            if (log.isInfoEnabled()) {
                log.info(
                    "Mensagem inbound duplicada ignorada: phoneNumber={} providerMessageId={}",
                    maskPhoneNumber(inboundMessage.phoneNumber()),
                    inboundMessage.providerMessageId()
                );
            }
            return false;
        }
    }

    private ConversationMessageType resolveInboundMessageType(InboundWhatsAppMessage inboundMessage) {
        if ("button_reply".equals(inboundMessage.messageType())) {
            return ConversationMessageType.INTERACTIVE_BUTTON;
        }
        if ("list_reply".equals(inboundMessage.messageType())) {
            return ConversationMessageType.INTERACTIVE_LIST;
        }
        if (inboundMessage.interactiveReplyId() != null && !inboundMessage.interactiveReplyId().isBlank()) {
            return ConversationMessageType.INTERACTIVE_BUTTON;
        }
        if (hasText(inboundMessage)) {
            return ConversationMessageType.TEXT;
        }
        return ConversationMessageType.SYSTEM;
    }

    private String resolveInboundRawText(InboundWhatsAppMessage inboundMessage) {
        if (hasText(inboundMessage)) {
            return inboundMessage.textBody();
        }
        if (inboundMessage.interactiveReplyTitle() != null && !inboundMessage.interactiveReplyTitle().isBlank()) {
            return inboundMessage.interactiveReplyTitle();
        }
        return blankToDefault(inboundMessage.interactiveReplyId(), NO_TEXT_FALLBACK);
    }

    private List<String> buildRecentMessagesForHandoff(String conversationId) {
        return conversationMessageGateway.findRecentByConversationId(conversationId, 5).stream()
            .map(message -> "%s: %s".formatted(message.senderType().name(), blankToDefault(message.rawText(), NO_TEXT_FALLBACK)))
            .toList();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean isDuplicateInboundMessage(InboundWhatsAppMessage inboundMessage) {
        String providerMessageId = inboundMessage.providerMessageId();
        if (providerMessageId == null || providerMessageId.isBlank()) {
            return false;
        }

        boolean duplicate = conversationMessageGateway.existsByProviderMessageId(providerMessageId);
        if (duplicate && log.isInfoEnabled()) {
            log.info(
                "Webhook duplicado ignorado: phoneNumber={} providerMessageId={}",
                maskPhoneNumber(inboundMessage.phoneNumber()),
                providerMessageId
            );
        }
        return duplicate;
    }

    private void logIncomingMessage(Conversation conversation, InboundWhatsAppMessage inboundMessage) {
        if (log.isInfoEnabled()) {
            log.info(
                "Mensagem recebida: phoneNumber={} type={} currentStep={}",
                maskPhoneNumber(inboundMessage.phoneNumber()),
                resolveMessageType(inboundMessage),
                conversation.currentStep()
            );
        }
    }
}
