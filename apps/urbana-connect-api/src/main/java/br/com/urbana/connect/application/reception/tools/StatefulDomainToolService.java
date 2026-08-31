package br.com.urbana.connect.application.reception.tools;

import br.com.urbana.connect.application.reception.CommercialPolicyService;
import br.com.urbana.connect.application.reception.TermsAcceptanceUseCase;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.CustomerFactType;
import br.com.urbana.connect.domain.reception.model.FactConfidence;
import br.com.urbana.connect.domain.reception.model.HumanHandoffNotification;
import br.com.urbana.connect.domain.reception.model.IcpObservationEvent;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.model.ReceptionEventIds;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.HumanHandoffNotificationGateway;
import br.com.urbana.connect.domain.reception.port.out.IcpObservationEventGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The six allowlisted tools mutate only authoritative Urbana state. Hermes
 * supplies intent and phrasing; it never receives a repository or raw Mongo
 * access.
 */
public final class StatefulDomainToolService implements DomainToolService {
    private static final String NOT_INFORMED = "NÃO INFORMADO";
    private static final String ENVIRONMENT = "ENVIRONMENT";
    private static final String CUSTOMER_MESSAGE = "customerMessage";
    public static final String HUMAN_HANDOFF_ACK =
            "Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.";
    private final CommercialPolicyService policy;
    private final ReceptionConversationGateway conversations;
    private final CustomerFactGateway facts;
    private final ReceptionTranscriptGateway transcript;
    private IcpObservationEventGateway icpObservations;
    private HumanHandoffNotificationGateway handoffNotifications;
    private TermsAcceptanceUseCase termsAcceptance;

    public StatefulDomainToolService(CommercialPolicyService policy,
                                     ReceptionConversationGateway conversations,
                                     CustomerFactGateway facts) {
        this(policy, conversations, facts, null);
    }

    public StatefulDomainToolService(CommercialPolicyService policy,
                                     ReceptionConversationGateway conversations,
                                     CustomerFactGateway facts,
                                     ReceptionTranscriptGateway transcript) {
        this(policy, conversations, facts, transcript, null, null);
    }

    public StatefulDomainToolService(CommercialPolicyService policy,
                                     ReceptionConversationGateway conversations,
                                     CustomerFactGateway facts,
                                     ReceptionTranscriptGateway transcript,
                                     IcpObservationEventGateway icpObservations) {
        this(policy, conversations, facts, transcript, icpObservations, null);
    }

    public StatefulDomainToolService(CommercialPolicyService policy,
                                     ReceptionConversationGateway conversations,
                                     CustomerFactGateway facts,
                                     ReceptionTranscriptGateway transcript,
                                     IcpObservationEventGateway icpObservations,
                                     HumanHandoffNotificationGateway handoffNotifications) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.transcript = transcript;
        this.icpObservations = icpObservations;
        this.handoffNotifications = handoffNotifications;
    }

    @Autowired(required = false)
    void setIcpObservationEventGateway(IcpObservationEventGateway icpObservations) {
        this.icpObservations = icpObservations;
    }

    @Autowired(required = false)
    void setHumanHandoffNotificationGateway(HumanHandoffNotificationGateway handoffNotifications) {
        this.handoffNotifications = handoffNotifications;
    }

    @Autowired(required = false)
    public void setTermsAcceptanceUseCase(TermsAcceptanceUseCase termsAcceptance) {
        this.termsAcceptance = termsAcceptance;
    }

    @Override
    public Map<String, Object> execute(DomainToolName toolName, String contactId, Map<String, Object> arguments) {
        throw new IllegalStateException("stateful domain tools require backend execution context");
    }

    @Override
    public Map<String, Object> execute(DomainToolName toolName, String contactId,
                                       Map<String, Object> arguments, ToolExecutionContext context) {
        Objects.requireNonNull(toolName, "toolName");
        require(contactId, "contactId");
        Objects.requireNonNull(context, "context");
        // A lease proves turn ownership, not that the conversation is still
        // automatable. Handoff is authoritative and disables all late tool
        // calls, including calls arriving while a stale lease remains alive.
        ReceptionConversation currentConversation = conversation(contactId);
        if (currentConversation.mode() == br.com.urbana.connect.domain.reception.model.ReceptionMode.HUMAN
                && toolName != DomainToolName.REQUEST_HUMAN_HANDOFF) {
            throw new DomainToolInvocationUseCase.DomainRejectionException(
                    "HUMAN_OWNS_CONVERSATION", "WAIT_FOR_HUMAN", List.of(),
                    "A arquiteta continuará este atendimento por aqui.");
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        try {
            return switch (toolName) {
                case GET_CUSTOMER_PROFILE -> profile(contactId, context.now());
                case UPDATE_CUSTOMER_FACT -> updateFact(contactId, args, context);
                case LIST_AVAILABLE_SERVICES -> listServices();
                case PREPARE_TERMS -> prepareTerms(contactId, args, context);
                case PREPARE_PAYMENT -> preparePayment(contactId, args, context);
                case REQUEST_HUMAN_HANDOFF -> handoff(contactId, args, context);
            };
        } catch (DomainToolInvocationUseCase.DomainRejectionException rejection) {
            throw rejection;
        } catch (IllegalArgumentException | IllegalStateException rejection) {
            throw safeRejection(toolName, args, currentConversation, rejection);
        }
    }

    private static DomainToolInvocationUseCase.DomainRejectionException safeRejection(
            DomainToolName toolName, Map<String, Object> args, ReceptionConversation conversation,
            RuntimeException rejection) {
        return switch (toolName) {
            case PREPARE_PAYMENT -> {
                if (isMissingPaymentMethod(args) || isPaymentMethodRejection(rejection)) {
                    yield paymentMethodRejection(isMissingPaymentMethod(args) ? List.of("method") : List.of());
                }
                if (isServiceSelectionRejection(rejection)) {
                    yield serviceSelectionRejection();
                }
                if (isTermsRejection(rejection) && (conversation.termsStatus()
                        != br.com.urbana.connect.domain.reception.model.TermsStatus.ACCEPTED
                        || conversation.activeTermsConsentId() == null
                        || rejection.getMessage().toLowerCase(Locale.ROOT).contains("durable terms"))) {
                    yield termsRejection();
                }
                yield new DomainToolInvocationUseCase.DomainRejectionException(
                        "BUSINESS_RULE_REJECTED", "ASK_FOR_CLARIFICATION", List.of(),
                        "Preciso confirmar uma informação antes de continuar.");
            }
            case PREPARE_TERMS -> {
                if (rejection.getMessage() != null && rejection.getMessage().contains("environment")) {
                    yield new DomainToolInvocationUseCase.DomainRejectionException(
                            "ENVIRONMENT_NOT_CONFIRMED", "ASK_FOR_ENVIRONMENT", List.of("environment"),
                            "Para preparar os termos, preciso confirmar qual ambiente você deseja contratar.");
                }
                yield new DomainToolInvocationUseCase.DomainRejectionException(
                        "SERVICE_NOT_CONFIRMED", "CONFIRM_SERVICE", List.of("serviceType"),
                        "Preciso confirmar o serviço escolhido antes de apresentar os termos.");
            }
            case UPDATE_CUSTOMER_FACT -> new DomainToolInvocationUseCase.DomainRejectionException(
                    "CUSTOMER_INFORMATION_INVALID", "ASK_FOR_CLARIFICATION", List.of(),
                    "Preciso confirmar essa informação antes de continuar.");
            case REQUEST_HUMAN_HANDOFF -> new DomainToolInvocationUseCase.DomainRejectionException(
                    "HANDOFF_REASON_REQUIRED", "ASK_FOR_HANDOFF_REASON", List.of("reason"),
                    "Posso chamar a arquiteta assim que você confirmar que deseja o atendimento humano.");
            default -> new DomainToolInvocationUseCase.DomainRejectionException(
                    "BUSINESS_RULE_REJECTED", "ASK_FOR_CLARIFICATION", List.of(),
                    "Preciso confirmar uma informação antes de continuar.");
        };
    }

    private static DomainToolInvocationUseCase.DomainRejectionException paymentMethodRejection(
            List<String> missingFields) {
        return new DomainToolInvocationUseCase.DomainRejectionException(
                "PAYMENT_METHOD_INVALID", "ASK_FOR_PAYMENT_METHOD", missingFields,
                "Para continuar, você prefere realizar o pagamento via PIX ou cartão de crédito?");
    }

    private static DomainToolInvocationUseCase.DomainRejectionException termsRejection() {
        return new DomainToolInvocationUseCase.DomainRejectionException(
                "TERMS_NOT_ACCEPTED", "ASK_FOR_CLEAR_ACCEPTANCE", List.of(),
                "Antes do pagamento, preciso do seu aceite claro dos termos.");
    }

    private static DomainToolInvocationUseCase.DomainRejectionException serviceSelectionRejection() {
        return new DomainToolInvocationUseCase.DomainRejectionException(
                "SERVICE_NOT_CONFIRMED", "CONFIRM_SERVICE", List.of("serviceType"),
                "Preciso confirmar o serviço escolhido antes de continuar.");
    }

    private static boolean isMissingPaymentMethod(Map<String, Object> args) {
        Object method = args.get("method");
        return method == null || method.toString().isBlank();
    }

    private static boolean isPaymentMethodRejection(RuntimeException rejection) {
        String message = rejection.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("payment method");
    }

    private static boolean isServiceSelectionRejection(RuntimeException rejection) {
        String message = rejection.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("service does not match")
                || normalized.contains("service is not present")
                || normalized.contains("service must be selected")
                || normalized.contains("catalog item")
                || normalized.contains("servicetype");
    }

    private static boolean isTermsRejection(RuntimeException rejection) {
        String message = rejection.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("terms") || normalized.contains("acceptance");
    }

    private Map<String, Object> profile(String contactId, Instant now) {
        List<CustomerFact> current = facts.findCurrentByContactId(contactId, now);
        return Map.of("facts", current, "missingIcpFields", policy.missingIcpFields(current, now),
                "previousServices", current.stream().filter(f -> "SELECTED_SERVICE".equalsIgnoreCase(f.type()))
                        .map(CustomerFact::value).toList());
    }

    private Map<String, Object> updateFact(String contactId, Map<String, Object> args,
                                            ToolExecutionContext context) {
        String type = canonicalFactType(stringArg(args, "factType"));
        if (!CustomerFactType.isAllowed(type)) {
            throw new IllegalArgumentException("fact type is not allowlisted: " + type);
        }
        String value = canonicalFactValue(type, stringArg(args, "value"));
        FactConfidence requestedConfidence = confidence(args);
        FactConfidence confidence = requestedConfidence;
        Instant now = context.now();
        String sourceMessageId = sourceMessageIdFor(type, value, context);
        String sourceText = sourceText(sourceMessageId);
        if (isNotInformedValue(value)) {
            // A refusal is a valid terminal answer for optional ICP fields,
            // but it is not a real contracting environment. Keeping it
            // tentative prevents the sentinel from creating a commercial
            // unit or unlocking terms.
            confidence = ENVIRONMENT.equals(type)
                    ? FactConfidence.TENTATIVE
                    : FactConfidence.CONFIRMED;
        } else if (requestedConfidence == FactConfidence.CONFIRMED
                && !explicitlySupports(type, value, sourceText)) {
            // A model cannot turn an unsupported claim into a confirmed fact.
            // Preserve the observation as tentative for a later confirmation.
            confidence = FactConfidence.TENTATIVE;
        }
        List<CustomerFact> current = facts.findCurrentByContactId(contactId, now);
        CustomerFact newFact = new CustomerFact(contactId, type, value, confidence,
                sourceMessageId, now);
        current.stream().filter(f -> type.equalsIgnoreCase(f.type()) && f.supersededBy() == null)
                .forEach(previous -> facts.save(previous.supersede(newFact.id(), now)));
        CustomerFact saved = facts.save(newFact);
        if ("SELECTED_SERVICE".equals(type)) {
            ReceptionConversation conversation = conversation(contactId);
            conversations.save(policy.selectService(conversation, value, now));
        }
        if (ENVIRONMENT.equals(type) && saved.confidence() == FactConfidence.CONFIRMED
                && !isNotInformedValue(saved.value())) {
            ReceptionConversation conversation = conversation(contactId);
            String unitId = contractingUnitId(conversation.id(), sourceMessageId, value);
            conversations.save(conversation.bindContractingUnit(unitId, value, sourceMessageId, now));
        }
        return Map.of("status", "RECORDED", "factType", saved.type(), "value", saved.value(),
                "confidence", saved.confidence().name());
    }

    private Map<String, Object> listServices() {
        List<Map<String, Object>> values = policy.services().stream().map(service -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("serviceType", service.serviceType());
            value.put("name", service.name());
            value.put("description", service.description());
            value.put("price", service.price().toPlainString());
            value.put("scope", service.scope());
            value.put("areaRule", service.areaRule().description());
            value.put("deliverables", service.deliverables());
            value.put("process", service.process());
            value.put("responsibilities", service.responsibilities());
            value.put("exclusions", service.exclusions());
            value.put("support", service.support());
            value.put("resources", Map.of("terms", service.termsUrl(), "payment", service.paymentUrl(),
                    "briefing", service.briefingUrl()));
            return value;
        }).toList();
        return Map.of("services", values);
    }

    private Map<String, Object> prepareTerms(String contactId, Map<String, Object> args,
                                              ToolExecutionContext context) {
        Instant now = context.now();
        ReceptionConversation conversation = conversation(contactId);
        if (conversation.contractingUnitId() == null || conversation.environmentLabel() == null
                || conversation.environmentSourceMessageId() == null) {
            throw new IllegalStateException("environment must be explicitly bound before terms");
        }
        String requested = stringArg(args, "serviceType");
        String canonicalRequested = policy.service(requested).serviceType();
        if (conversation.selectedService() == null) {
            conversation = conversations.save(policy.selectService(conversation, canonicalRequested, now));
        } else if (!conversation.selectedService().equalsIgnoreCase(canonicalRequested)) {
            throw new IllegalStateException("service does not match the selected catalog item");
        }
        List<CustomerFact> currentFacts = facts.findCurrentByContactId(contactId, now);
        boolean firstTermsPresentation = conversation.termsStatus()
                == br.com.urbana.connect.domain.reception.model.TermsStatus.NOT_PRESENTED
                || conversation.termsStatus()
                == br.com.urbana.connect.domain.reception.model.TermsStatus.DECLINED;
        if (firstTermsPresentation) {
            recordIcpSkippedBeforeTerms(conversation, currentFacts, context);
        }
        ReceptionConversation presented = policy.presentTerms(conversation, facts.findByContactId(contactId), now);
        conversations.save(presented);
        return Map.of("status", "PRESENTED", "serviceType", presented.selectedService(),
                "url", policy.termsUrl(presented.selectedService()));
    }

    private Map<String, Object> preparePayment(String contactId, Map<String, Object> args,
                                                ToolExecutionContext context) {
        Instant now = context.now();
        ReceptionConversation conversation = conversation(contactId);
        String serviceType = stringArg(args, "serviceType");
        String canonicalServiceType = policy.service(serviceType).serviceType();
        if (conversation.selectedService() == null
                || !conversation.selectedService().equalsIgnoreCase(canonicalServiceType)) {
            throw new IllegalStateException("service does not match the selected catalog item");
        }
        if (conversation.termsStatus() != br.com.urbana.connect.domain.reception.model.TermsStatus.ACCEPTED
                || conversation.activeTermsConsentId() == null) {
            throw new IllegalStateException("payment requires accepted terms with durable consent evidence");
        }
        if (termsAcceptance == null) {
            throw new IllegalStateException("durable terms acceptance evidence is unavailable");
        }
        termsAcceptance.requireAcceptedEvidence(conversation);
        ReceptionConversation prepared = policy.preparePayment(conversation, facts.findByContactId(contactId),
                stringArg(args, "method"), now);
        if (prepared != conversation) {
            conversations.save(prepared);
        }
        return paymentPreparationResult(prepared, conversation.paymentStatus());
    }

    private Map<String, Object> paymentPreparationResult(ReceptionConversation conversation,
                                                         PaymentStatus previousStatus) {
        if (previousStatus == PaymentStatus.NOT_STARTED || previousStatus == PaymentStatus.REJECTED) {
            return Map.of("status", "PREPARED", "serviceType", conversation.selectedService(),
                    "instruction", policy.paymentUrl(conversation.selectedService()),
                    "nextAction", "AWAIT_PAYMENT_PROOF",
                    CUSTOMER_MESSAGE, "Pagamento preparado. No link da POC, que é uma simulação, considere 1 serviço para cada ambiente contratado. Depois do pagamento, envie o comprovante por aqui.");
        }
        return switch (conversation.paymentStatus()) {
            case PREPARED -> Map.of("status", "ALREADY_PREPARED", "serviceType", conversation.selectedService(),
                    "nextAction", "AWAIT_PAYMENT_PROOF",
                    CUSTOMER_MESSAGE, "O pagamento já foi preparado. Aguardo o comprovante por aqui.");
            case PROOF_RECEIVED -> Map.of("status", "PROOF_RECEIVED", "serviceType", conversation.selectedService(),
                    "nextAction", "AWAIT_PAYMENT_APPROVAL",
                    CUSTOMER_MESSAGE, "O comprovante já foi recebido e aguarda validação humana.");
            case CONFIRMED -> Map.of("status", "CONFIRMED", "serviceType", conversation.selectedService(),
                    "nextAction", "NONE",
                    CUSTOMER_MESSAGE, "O pagamento já foi confirmado pela arquiteta.");
            case NOT_STARTED, REJECTED -> Map.of("status", conversation.paymentStatus().name(),
                    "serviceType", conversation.selectedService(), "nextAction", "NONE",
                    CUSTOMER_MESSAGE, "Preciso confirmar essa etapa antes de continuar.");
        };
    }

    private Map<String, Object> handoff(String contactId, Map<String, Object> args,
                                        ToolExecutionContext context) {
        ReceptionConversation conversation = conversation(contactId);
        ReceptionConversation human = conversation.requestHumanHandoff(stringArg(args, "reason"), context.now());
        if (human != conversation) {
            human = conversations.save(human);
            persistHandoffNotification(human, context.lease().turnId(), context.now());
        }
        persistHandoffAck(human, context.lease().turnId(), context.now());
        return Map.of("status", "TRANSFERRED", "ownership", "HUMAN",
                "ackMessage", HUMAN_HANDOFF_ACK, "handoffId", handoffId(human));
    }

    private void recordIcpSkippedBeforeTerms(ReceptionConversation conversation,
                                             List<CustomerFact> currentFacts,
                                             ToolExecutionContext context) {
        if (icpObservations == null) {
            return;
        }
        List<String> missing = policy.missingIcpFields(currentFacts, context.now());
        if (missing.isEmpty()) {
            return;
        }
        String idempotencyKey = "icp-before-terms:" + conversation.id() + ":"
                + context.lease().turnId() + ":" + conversation.selectedService();
        IcpObservationEvent event = IcpObservationEvent.beforeTerms(conversation.id(),
                context.lease().turnId(), conversation.selectedService(), missing, idempotencyKey,
                context.now());
        try {
            icpObservations.appendIfAbsent(event);
        } catch (RuntimeException ignored) {
            // Observability must not turn a valid commercial continuation into
            // a customer-visible failure. The durable tool ledger still records
            // the successful terms transition.
        }
    }

    private void persistHandoffNotification(ReceptionConversation human, String turnId, Instant now) {
        if (handoffNotifications == null) {
            return;
        }
        List<CustomerFact> currentFacts = facts.findCurrentByContactId(human.contactId(), now);
        List<String> missing = policy.missingIcpFields(currentFacts, now);
        List<String> present = policy.mandatoryIcpFields().stream()
                .filter(field -> !missing.contains(field)).toList();
        HumanHandoffNotification notification = HumanHandoffNotification.create(
                handoffId(human), human.id(), turnId, human.handoffReason(), human.selectedService(),
                human.commercialStage().name(), human.paymentStatus().name(), present, missing, now);
        try {
            handoffNotifications.notifyIfAbsent(notification);
        } catch (RuntimeException ignored) {
            // The visible acknowledgement and HUMAN ownership remain safe even
            // when an optional notification sink is unavailable.
        }
    }

    private void persistHandoffAck(ReceptionConversation human, String correlationId, Instant now) {
        if (transcript == null) {
            return;
        }
        transcript.appendIfAbsent(new ReceptionMessage(UUID.randomUUID().toString(), handoffAckEventId(human),
                correlationId, human.id(), human.contactId(), ReceptionMessageDirection.OUTBOUND,
                ReceptionMessageSender.URBA, ReceptionMessageType.TEXT, HUMAN_HANDOFF_ACK,
                null, null, now));
    }

    public static String handoffId(ReceptionConversation human) {
        String material = human.id() + ":" + human.version();
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String handoffAckEventId(ReceptionConversation human) {
        return ReceptionEventIds.outbound("human-handoff:" + handoffId(human), human.id());
    }

    private ReceptionConversation conversation(String contactId) {
        return conversations.findByContactId(contactId)
                .orElseThrow(() -> new IllegalStateException("conversation does not exist"));
    }

    private static FactConfidence confidence(Map<String, Object> args) {
        Object value = args.get("confidence");
        return value == null ? FactConfidence.CONFIRMED : FactConfidence.valueOf(value.toString().toUpperCase(Locale.ROOT));
    }

    private String sourceMessageIdFor(String type, String value, ToolExecutionContext context) {
        if (transcript == null) {
            return context.sourceMessageId();
        }
        String supported = context.sourceMessageIds().stream()
                .filter(eventId -> explicitlySupports(type, value, sourceText(eventId)))
                .reduce((first, last) -> last)
                .orElse(null);
        return supported == null ? context.sourceMessageId() : supported;
    }

    private String sourceText(String eventId) {
        if (transcript == null) {
            return "";
        }
        return transcript.findByEventId(eventId).map(message ->
                message.text() == null ? "" : message.text()).orElse("");
    }

    private static boolean explicitlySupports(String type, String value, String source) {
        if (isNotInformedValue(value)) {
            return !ENVIRONMENT.equals(type);
        }
        if (source == null || source.isBlank()) return false;
        String normalizedSource = normalizeEvidence(source);
        String normalizedValue = normalizeEvidence(value.replace('_', ' '));
        if ("FIRST_TIME_HIRING".equals(type)) {
            if ("SIM".equals(value)) {
                return !containsNegation(normalizedSource)
                        && (normalizedSource.contains("primeira vez")
                        || normalizedSource.matches(".*\\b(sim|yes|true)\\b.*"));
            }
            if ("NÃO".equals(value)) {
                return containsNegation(normalizedSource)
                        || normalizedSource.matches(".*\\b(no|false)\\b.*");
            }
            return false;
        }
        if ("SELECTED_SERVICE".equals(type)) {
            // A correction may negate the previous service in the same
            // sentence while positively naming the replacement service.
            if (normalizedSource.contains(normalizedValue)) {
                return true;
            }
            // DECOR is retained only as an input alias for Decor Interiores.
            // The persisted value is canonical, so evidence such as
            // "Quero contratar Decor" must still support the canonical fact.
            return "DECOR_INTERIORES".equals(value)
                    && normalizedSource.matches(".*\\bdecor\\b.*");
        }
        if (containsNegation(normalizedSource)) {
            return false;
        }
        return switch (type) {
            case "PRONOUN_PREFERENCE" -> normalizedSource.contains(normalizedValue);
            case "OCCUPATION", "NEED" -> normalizedSource.contains(normalizedValue);
            case ENVIRONMENT -> containsWholePhrase(normalizedSource, normalizedValue);
            case "SELECTED_SERVICE" -> normalizedSource.contains(normalizedValue);
            default -> false;
        };
    }

    private static boolean containsWholePhrase(String normalizedSource, String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return false;
        }
        String sourceWords = normalizedSource.replaceAll("[^\\p{L}\\p{N}]+", " ").trim()
                .replaceAll("\\s+", " ");
        String valueWords = normalizedValue.replaceAll("[^\\p{L}\\p{N}]+", " ").trim()
                .replaceAll("\\s+", " ");
        if (valueWords.isBlank()) {
            return false;
        }
        Pattern phrase = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(valueWords)
                + "(?![\\p{L}\\p{N}])");
        return phrase.matcher(sourceWords).find();
    }

    private static boolean containsNegation(String normalizedSource) {
        return normalizedSource.matches(".*\\b(nao|nunca|jamais|nem)\\b.*");
    }

    private static String normalizeEvidence(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private String canonicalFactValue(String type, String value) {
        return switch (type) {
            case "PRONOUN_PREFERENCE", "OCCUPATION" -> canonicalFreeText(value);
            case "FIRST_TIME_HIRING" -> canonicalFirstTimeHiring(value);
            case "SELECTED_SERVICE" -> canonicalService(value);
            case "NEED" -> value.trim();
            case ENVIRONMENT -> isNotInformedValue(value) ? NOT_INFORMED : value.trim();
            default -> value.trim();
        };
    }

    private static String canonicalFreeText(String value) {
        String trimmed = value.trim();
        return isNotInformedValue(trimmed) ? NOT_INFORMED : trimmed;
    }

    private static String canonicalFirstTimeHiring(String value) {
        String normalized = normalizeToken(value);
        if (isNotInformedValue(value)) {
            return NOT_INFORMED;
        }
        if (normalized.contains("NAO") || normalized.contains("NUNCA") || normalized.contains("JAMAIS")) {
            return "NÃO";
        }
        if (normalized.contains("PRIMEIRA_VEZ")) {
            return "SIM";
        }
        return switch (normalized) {
            case "YES", "SIM", "TRUE", "1" -> "SIM";
            case "NO", "NAO", "NÃO", "FALSE", "0" -> "NÃO";
            default -> throw new IllegalArgumentException("first-time hiring value is not supported: " + value);
        };
    }

    private static boolean isNotInformedValue(String value) {
        return switch (normalizeToken(value)) {
            case "NAO_INFORMADO", "PREFIRO_NAO_RESPONDER", "PREFIRO_NAO_INFORMAR",
                    "PREFER_NOT_TO_ANSWER", "NAO_QUERO_INFORMAR", "NAO_QUERO_RESPONDER",
                    "SEM_INFORMACAO" -> true;
            default -> false;
        };
    }

    private String canonicalService(String value) {
        String normalized = normalizeToken(value);
        if ("DECOR".equals(normalized)) {
            return "DECOR_INTERIORES";
        }
        return policy.services().stream()
                .filter(service -> normalizeToken(service.serviceType()).equals(normalized)
                        || normalizeToken(service.name()).equals(normalized))
                .map(CommercialPolicyService.ServiceFixture::serviceType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "selected service is not present in the approved catalog: " + value));
    }

    private static String normalizeToken(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", "_");
    }

    private static String contractingUnitId(String conversationId, String sourceMessageId, String label) {
        try {
            String canonicalLabel = normalizeEvidence(label);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    (conversationId + ":" + sourceMessageId + ":" + canonicalLabel).getBytes(StandardCharsets.UTF_8));
            return "unit_" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.toString().trim();
    }

    /**
     * Keep the persisted fact vocabulary canonical while tolerating a small,
     * explicit set of labels that language models commonly emit when they
     * paraphrase the business field name.
     */
    private static String canonicalFactType(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim();
        return switch (normalized) {
            case "PRONOUN PREFERENCE", "PRONOUNS", "PRONOME", "PRONOMES",
                    "PREFERRED PRONOUN", "PREFERRED PRONOUNS" -> "PRONOUN_PREFERENCE";
            case "FIRST TIME HIRING", "FIRST TIME", "HIRING EXPERIENCE",
                    "EXPERIENCE WITH DESIGN HIRING", "EXPERIENCIA COM CONTRATACAO DE DESIGN" ->
                    "FIRST_TIME_HIRING";
            case "OCCUPATION", "PROFESSION", "PROFISSAO" -> "OCCUPATION";
            case "SELECTED SERVICE", "SERVICE", "SERVICO", "SELECTED SERVICO" ->
                    "SELECTED_SERVICE";
            case "NEED", "NECESSIDADE", "PROJECT NEED" -> "NEED";
            case ENVIRONMENT, "AMBIENTE" -> ENVIRONMENT;
            default -> normalized.replace(' ', '_');
        };
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
