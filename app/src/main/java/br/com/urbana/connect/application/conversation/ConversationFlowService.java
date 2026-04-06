package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.AiContext;
import br.com.urbana.connect.domain.conversation.model.AiInterpretation;
import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.HumanHandoffRequest;
import br.com.urbana.connect.domain.conversation.model.IntentType;
import br.com.urbana.connect.domain.conversation.model.ServiceSummary;
import br.com.urbana.connect.domain.conversation.port.out.AiGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.domain.conversation.port.out.HumanHandoffGateway;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ConversationFlowService {

    private static final Logger log = LoggerFactory.getLogger(ConversationFlowService.class);
    private static final Pattern HUMAN_HANDOFF_PATTERN = Pattern.compile(
        "(\\bhumano\\b|falar com algu[eé]m|atendimento humano|atendente|pessoa real)"
    );

    private final ConversationLifecycleService conversationLifecycleService;
    private final ConversationGateway conversationGateway;
    private final ServiceCatalogGateway serviceCatalogGateway;
    private final WhatsAppMessageGateway whatsAppMessageGateway;
    private final AiGateway aiGateway;
    private final HumanHandoffGateway humanHandoffGateway;

    public ConversationFlowService(
            ConversationLifecycleService conversationLifecycleService,
            ConversationGateway conversationGateway,
            ServiceCatalogGateway serviceCatalogGateway,
            WhatsAppMessageGateway whatsAppMessageGateway,
            AiGateway aiGateway,
            HumanHandoffGateway humanHandoffGateway) {
        this.conversationLifecycleService = conversationLifecycleService;
        this.conversationGateway = conversationGateway;
        this.serviceCatalogGateway = serviceCatalogGateway;
        this.whatsAppMessageGateway = whatsAppMessageGateway;
        this.aiGateway = aiGateway;
        this.humanHandoffGateway = humanHandoffGateway;
    }

    public Conversation handleIncomingMessage(InboundWhatsAppMessage inboundMessage, Instant receivedAt) {
        Conversation conversation = conversationLifecycleService.resumeOrStart(inboundMessage.phoneNumber(), receivedAt);
        List<ServiceCatalogItem> availableServices = serviceCatalogGateway.findAvailable();

        log.info(
            "Mensagem recebida: phoneNumber={} type={} currentStep={}",
            maskPhoneNumber(inboundMessage.phoneNumber()),
            resolveMessageType(inboundMessage),
            conversation.currentStep()
        );

        if (isHumanHandoffRequested(inboundMessage.textBody())) {
            handleHumanHandoff(conversation, inboundMessage, receivedAt);
            return conversation;
        }

        return switch (conversation.currentStep()) {
            case GREETING -> handleGreeting(conversation, inboundMessage, availableServices, receivedAt);
            case TRIAGE_GUIDED -> handleGuidedTriage(conversation, inboundMessage, availableServices, receivedAt);
            case TRIAGE_DIRECT -> handleDirectTriage(conversation, inboundMessage, availableServices, receivedAt);
            case AWAITING_CONFIRMATION -> handleAwaitingConfirmation(conversation, inboundMessage, availableServices, receivedAt);
            case AWAITING_TERMS -> handleAwaitingTerms(conversation, inboundMessage, receivedAt);
            case AWAITING_PAYMENT_METHOD -> handleAwaitingPaymentMethod(conversation, inboundMessage, availableServices, receivedAt);
            default -> conversation;
        };
    }

    private Conversation handleGreeting(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        String replyId = inboundMessage.interactiveReplyId();

        if (isFirstTouch(conversation, receivedAt)) {
            sendSafely(
                inboundMessage.phoneNumber(),
                conversation.currentStep(),
                () -> whatsAppMessageGateway.sendGreeting(inboundMessage.phoneNumber())
            );
            return conversation;
        }

        if (hasText(inboundMessage)) {
            AiInterpretation interpretation = interpret(conversation, inboundMessage, availableServices);
            if (interpretation.intent() == IntentType.AFFIRMATION) {
                Conversation updated = saveTransition(
                    conversation,
                    conversation.moveTo(ConversationStep.TRIAGE_GUIDED, receivedAt),
                    inboundMessage.phoneNumber(),
                    "greeting_ai_affirmation"
                );
                sendSafely(
                    inboundMessage.phoneNumber(),
                    updated.currentStep(),
                    () -> whatsAppMessageGateway.sendGuidedTriageOptions(inboundMessage.phoneNumber(), availableServices)
                );
                return updated;
            }

            if (interpretation.intent() == IntentType.NEGATION) {
                Conversation updated = saveTransition(
                    conversation,
                    conversation.moveTo(ConversationStep.TRIAGE_DIRECT, receivedAt),
                    inboundMessage.phoneNumber(),
                    "greeting_ai_negation"
                );
                sendSafely(
                    inboundMessage.phoneNumber(),
                    updated.currentStep(),
                    () -> whatsAppMessageGateway.sendDirectTriageOptions(inboundMessage.phoneNumber(), availableServices)
                );
                return updated;
            }
        }

        if ("YES_HELP".equals(replyId)) {
            Conversation updated = saveTransition(
                conversation,
                conversation.moveTo(ConversationStep.TRIAGE_GUIDED, receivedAt),
                inboundMessage.phoneNumber(),
                "greeting_yes_help"
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendGuidedTriageOptions(inboundMessage.phoneNumber(), availableServices)
            );
            return updated;
        }

        if ("NO_HELP".equals(replyId)) {
            Conversation updated = saveTransition(
                conversation,
                conversation.moveTo(ConversationStep.TRIAGE_DIRECT, receivedAt),
                inboundMessage.phoneNumber(),
                "greeting_no_help"
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendDirectTriageOptions(inboundMessage.phoneNumber(), availableServices)
            );
            return updated;
        }

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendGreeting(inboundMessage.phoneNumber())
        );
        return conversation;
    }

    private Conversation handleGuidedTriage(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        if (hasText(inboundMessage)) {
            Optional<ServiceCatalogItem> interpretedService = resolveInterpretedService(conversation, inboundMessage, availableServices);
            if (interpretedService.isPresent()) {
                return moveToConfirmation(conversation, inboundMessage.phoneNumber(), interpretedService.get(), receivedAt);
            }
        }

        Optional<ServiceCatalogItem> selectedService = resolveSelectedService(availableServices, inboundMessage.interactiveReplyId());
        if (selectedService.isPresent()) {
            return moveToConfirmation(conversation, inboundMessage.phoneNumber(), selectedService.get(), receivedAt);
        }

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendGuidedTriageOptions(inboundMessage.phoneNumber(), availableServices)
        );
        return conversation;
    }

    private Conversation handleDirectTriage(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        if (hasText(inboundMessage)) {
            Optional<ServiceCatalogItem> interpretedService = resolveInterpretedService(conversation, inboundMessage, availableServices);
            if (interpretedService.isPresent()) {
                return moveToConfirmation(conversation, inboundMessage.phoneNumber(), interpretedService.get(), receivedAt);
            }
        }

        Optional<ServiceCatalogItem> selectedService = resolveSelectedService(availableServices, inboundMessage.interactiveReplyId());
        if (selectedService.isPresent()) {
            return moveToConfirmation(conversation, inboundMessage.phoneNumber(), selectedService.get(), receivedAt);
        }

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendDirectTriageOptions(inboundMessage.phoneNumber(), availableServices)
        );
        return conversation;
    }

    private Conversation moveToConfirmation(
            Conversation conversation,
            String phoneNumber,
            ServiceCatalogItem selectedService,
            Instant receivedAt) {
        Conversation updated = saveTransition(
            conversation,
            conversation.selectService(selectedService.type(), ConversationStep.AWAITING_CONFIRMATION, receivedAt),
            phoneNumber,
            "service_selected"
        );

        sendSafely(
            phoneNumber,
            updated.currentStep(),
            () -> whatsAppMessageGateway.sendServicePresentation(phoneNumber, selectedService)
        );
        return updated;
    }

    private Conversation handleAwaitingConfirmation(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        String replyId = inboundMessage.interactiveReplyId();

        if (hasText(inboundMessage)) {
            AiInterpretation interpretation = interpret(conversation, inboundMessage, availableServices);
            if (interpretation.intent() == IntentType.AFFIRMATION) {
                Conversation updated = saveTransition(
                    conversation,
                    conversation.moveTo(ConversationStep.AWAITING_TERMS, receivedAt),
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

            if (interpretation.intent() == IntentType.NEGATION) {
                Conversation updated = saveTransition(
                    conversation,
                    conversation.moveTo(ConversationStep.TRIAGE_DIRECT, receivedAt),
                    inboundMessage.phoneNumber(),
                    "confirmation_ai_negation"
                );
                sendSafely(
                    inboundMessage.phoneNumber(),
                    updated.currentStep(),
                    () -> whatsAppMessageGateway.sendDirectTriageOptions(inboundMessage.phoneNumber(), availableServices)
                );
                return updated;
            }
        }

        if ("CONFIRM_SERVICE".equals(replyId)) {
            Conversation updated = saveTransition(
                conversation,
                conversation.moveTo(ConversationStep.AWAITING_TERMS, receivedAt),
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
                conversation.moveTo(ConversationStep.TRIAGE_DIRECT, receivedAt),
                inboundMessage.phoneNumber(),
                "confirmation_reselect"
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendDirectTriageOptions(inboundMessage.phoneNumber(), availableServices)
            );
            return updated;
        }

        return serviceCatalogGateway.findByType(conversation.selectedService())
            .map(service -> {
                sendSafely(
                    inboundMessage.phoneNumber(),
                    conversation.currentStep(),
                    () -> whatsAppMessageGateway.sendServicePresentation(inboundMessage.phoneNumber(), service)
                );
                return conversation;
            })
            .orElse(conversation);
    }

    private Conversation handleAwaitingTerms(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            Instant receivedAt) {
        if (containsTermsAcceptance(inboundMessage.textBody())
                || interpret(conversation, inboundMessage, List.of()).intent() == IntentType.TERMS_ACCEPTANCE) {
            Conversation updated = saveTransition(
                conversation,
                conversation.moveTo(ConversationStep.AWAITING_PAYMENT_METHOD, receivedAt),
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

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendTermsOfUse(inboundMessage.phoneNumber())
        );
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
                    conversation.selectPaymentMethod(paymentMethod, ConversationStep.PAYMENT_LINK_SENT, receivedAt),
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

            log.error(
                "Servico {} nao encontrado para enviar link de pagamento para {}",
                conversation.selectedService(),
                maskPhoneNumber(inboundMessage.phoneNumber())
            );
            Conversation updated = saveTransition(
                conversation,
                conversation.moveTo(ConversationStep.TRIAGE_DIRECT, receivedAt),
                inboundMessage.phoneNumber(),
                "payment_service_missing"
            );
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendDirectTriageOptions(inboundMessage.phoneNumber(), availableServices)
            );
            return updated;
        }

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendPaymentMethodOptions(inboundMessage.phoneNumber())
        );
        return conversation;
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

    private Optional<ServiceCatalogItem> resolveInterpretedService(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices) {
        AiInterpretation interpretation = interpret(conversation, inboundMessage, availableServices);
        if (interpretation.intent() != IntentType.SERVICE_SELECTION || interpretation.selectedService() == null) {
            return Optional.empty();
        }

        return availableServices.stream()
            .filter(service -> service.type() == interpretation.selectedService())
            .findFirst();
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

    private Conversation saveTransition(Conversation previous, Conversation next, String phoneNumber, String reason) {
        Conversation saved = conversationGateway.save(next);
        if (previous.currentStep() != saved.currentStep()) {
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
        log.info(
            "Solicitacao de handoff humano recebida para {} na etapa {}",
            maskPhoneNumber(inboundMessage.phoneNumber()),
            conversation.currentStep()
        );
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
                inboundMessage.textBody(),
                receivedAt
            ));
        } catch (RuntimeException exception) {
            log.error(
                "Falha ao notificar handoff humano para {} na etapa {}: {}",
                maskPhoneNumber(inboundMessage.phoneNumber()),
                conversation.currentStep(),
                exception.getMessage()
            );
        }
    }

    private boolean containsTermsAcceptance(String textBody) {
        if (textBody == null || textBody.isBlank()) {
            return false;
        }

        return textBody.toLowerCase().contains("aceito");
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
            case "PAYMENT_CARD" -> "CARTÃO";
            default -> null;
        };
    }

    private String resolvePaymentMethodFromText(String textBody) {
        if (textBody == null || textBody.isBlank()) {
            return null;
        }

        String normalized = textBody.toLowerCase();
        if (normalized.contains("pix")) {
            return "PIX";
        }
        if (normalized.contains("cart")) {
            return "CARTÃO";
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

    private AiInterpretation interpret(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices) {
        if (!hasText(inboundMessage)) {
            return AiInterpretation.unknown();
        }

        return Optional.ofNullable(aiGateway.interpret(new AiContext(
            conversation.currentStep(),
            inboundMessage.textBody(),
            availableServices.stream().map(this::toServiceSummary).toList(),
            ""
        ))).orElse(AiInterpretation.unknown());
    }

    private ServiceSummary toServiceSummary(ServiceCatalogItem service) {
        return new ServiceSummary(service.type(), service.name(), service.scenarioText());
    }

    private boolean hasText(InboundWhatsAppMessage inboundMessage) {
        return inboundMessage.textBody() != null && !inboundMessage.textBody().isBlank();
    }

    private boolean isFirstTouch(Conversation conversation, Instant receivedAt) {
        return conversation.createdAt().equals(receivedAt)
            && conversation.updatedAt().equals(receivedAt)
            && conversation.selectedService() == null;
    }
}
