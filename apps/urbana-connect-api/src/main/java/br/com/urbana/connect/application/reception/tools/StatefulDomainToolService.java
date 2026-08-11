package br.com.urbana.connect.application.reception.tools;

import br.com.urbana.connect.application.reception.CommercialPolicyService;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.CustomerFactType;
import br.com.urbana.connect.domain.reception.model.FactConfidence;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;

import java.text.Normalizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The six allowlisted tools mutate only authoritative Urbana state. Hermes
 * supplies intent and phrasing; it never receives a repository or raw Mongo
 * access.
 */
public final class StatefulDomainToolService implements DomainToolService {
    private final CommercialPolicyService policy;
    private final ReceptionConversationGateway conversations;
    private final CustomerFactGateway facts;
    private final ReceptionTranscriptGateway transcript;

    public StatefulDomainToolService(CommercialPolicyService policy,
                                     ReceptionConversationGateway conversations,
                                     CustomerFactGateway facts) {
        this(policy, conversations, facts, null);
    }

    public StatefulDomainToolService(CommercialPolicyService policy,
                                     ReceptionConversationGateway conversations,
                                     CustomerFactGateway facts,
                                     ReceptionTranscriptGateway transcript) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.transcript = transcript;
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
        if (currentConversation.mode() == br.com.urbana.connect.domain.reception.model.ReceptionMode.HUMAN) {
            throw new IllegalStateException("domain tools are disabled in HUMAN mode");
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        return switch (toolName) {
            case GET_CUSTOMER_PROFILE -> profile(contactId, context.now());
            case UPDATE_CUSTOMER_FACT -> updateFact(contactId, args, context);
            case LIST_AVAILABLE_SERVICES -> listServices();
            case PREPARE_TERMS -> prepareTerms(contactId, args, context.now());
            case PREPARE_PAYMENT -> preparePayment(contactId, args, context);
            case REQUEST_HUMAN_HANDOFF -> handoff(contactId, args, context.now());
        };
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
        String sourceText = sourceText(context);
        if (requestedConfidence == FactConfidence.CONFIRMED && !explicitlySupports(type, value, sourceText)) {
            // A model cannot turn an unsupported claim into a confirmed fact.
            // Preserve the observation as tentative for a later confirmation.
            confidence = FactConfidence.TENTATIVE;
        }
        List<CustomerFact> current = facts.findCurrentByContactId(contactId, now);
        current.stream().filter(f -> type.equalsIgnoreCase(f.type()) && f.supersededBy() == null)
                .forEach(previous -> facts.save(previous.supersede(java.util.UUID.randomUUID().toString(), now)));
        CustomerFact saved = facts.save(new CustomerFact(contactId, type, value, confidence,
                context.sourceMessageId(), now));
        if ("SELECTED_SERVICE".equals(type)) {
            ReceptionConversation conversation = conversation(contactId);
            conversations.save(policy.selectService(conversation, value, now));
        }
        return Map.of("status", "RECORDED", "factType", saved.type(), "value", saved.value(),
                "confidence", saved.confidence().name());
    }

    private Map<String, Object> listServices() {
        return Map.of("services", policy.services().stream().map(service -> Map.of(
                "serviceType", service.serviceType(), "name", service.name(), "description", service.description(),
                "price", service.price().toPlainString())).toList());
    }

    private Map<String, Object> prepareTerms(String contactId, Map<String, Object> args, Instant now) {
        ReceptionConversation conversation = conversation(contactId);
        String requested = stringArg(args, "serviceType");
        if (conversation.selectedService() == null) {
            conversation = conversations.save(policy.selectService(conversation, requested, now));
        } else if (!conversation.selectedService().equalsIgnoreCase(requested)) {
            throw new IllegalStateException("service does not match the selected catalog item");
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
        if (conversation.selectedService() == null || !conversation.selectedService().equalsIgnoreCase(serviceType)) {
            throw new IllegalStateException("service does not match the selected catalog item");
        }
        if (conversation.termsStatus() == br.com.urbana.connect.domain.reception.model.TermsStatus.PRESENTED) {
            String source = sourceText(context);
            if (!acceptsTerms(source)) {
                throw new IllegalStateException("payment requires explicit terms acceptance in the bound inbound message");
            }
            conversation = conversations.save(policy.acceptTerms(conversation, now));
        }
        ReceptionConversation prepared = policy.preparePayment(conversation, facts.findByContactId(contactId),
                stringArg(args, "method"), now);
        conversations.save(prepared);
        return Map.of("status", "PREPARED", "serviceType", prepared.selectedService(),
                "instruction", policy.paymentUrl(prepared.selectedService()));
    }

    private Map<String, Object> handoff(String contactId, Map<String, Object> args, Instant now) {
        ReceptionConversation conversation = conversation(contactId);
        ReceptionConversation human = conversation.requestHumanHandoff(stringArg(args, "reason"), now);
        conversations.save(human);
        return Map.of("status", "HUMAN_MODE", "reason", human.handoffReason());
    }

    private ReceptionConversation conversation(String contactId) {
        return conversations.findByContactId(contactId)
                .orElseThrow(() -> new IllegalStateException("conversation does not exist"));
    }

    private static FactConfidence confidence(Map<String, Object> args) {
        Object value = args.get("confidence");
        return value == null ? FactConfidence.CONFIRMED : FactConfidence.valueOf(value.toString().toUpperCase(Locale.ROOT));
    }

    private String sourceText(ToolExecutionContext context) {
        if (transcript == null) {
            return "";
        }
        return transcript.findByEventId(context.sourceMessageId()).map(message ->
                message.text() == null ? "" : message.text()).orElse("");
    }

    private static boolean explicitlySupports(String type, String value, String source) {
        if (source == null || source.isBlank()) return false;
        String normalizedSource = normalizeEvidence(source);
        String normalizedValue = normalizeEvidence(value.replace('_', ' '));
        if ("PRONOUN_PREFERENCE".equals(type) && normalizedValue.contains("prefer not to answer")) {
            return normalizedSource.contains("prefiro nao responder")
                    || normalizedSource.contains("prefiro nao informar");
        }
        if ("FIRST_TIME_HIRING".equals(type) && !"YES".equalsIgnoreCase(value)) {
            return containsNegation(normalizedSource);
        }
        if ("SELECTED_SERVICE".equals(type)) {
            // A correction may negate the previous service in the same
            // sentence while positively naming the replacement service.
            return normalizedSource.contains(normalizedValue);
        }
        if (containsNegation(normalizedSource)) {
            return false;
        }
        return switch (type) {
            case "PRONOUN_PREFERENCE" -> normalizedSource.contains(normalizedValue.split(" ")[0]);
            case "FIRST_TIME_HIRING" -> "YES".equalsIgnoreCase(value)
                    && (normalizedSource.contains("primeira vez")
                    || normalizedSource.matches(".*\\b(sim|yes)\\b.*"));
            case "OCCUPATION", "NEED" -> normalizedSource.contains(normalizedValue);
            case "SELECTED_SERVICE" -> normalizedSource.contains(normalizedValue);
            default -> false;
        };
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
            case "PRONOUN_PREFERENCE" -> canonicalPronoun(value);
            case "FIRST_TIME_HIRING" -> canonicalFirstTimeHiring(value);
            case "OCCUPATION" -> normalizeToken(value);
            case "SELECTED_SERVICE" -> canonicalService(value);
            case "NEED" -> value.trim();
            default -> value.trim();
        };
    }

    private static String canonicalPronoun(String value) {
        return switch (normalizeToken(value)) {
            case "ELA", "ELA_DELA" -> "ELA_DELA";
            case "ELE", "ELE_DELE" -> "ELE_DELE";
            case "PREFIRO_NAO_RESPONDER", "PREFIRO_NAO_INFORMAR", "PREFER_NOT_TO_ANSWER" ->
                    CommercialPolicyService.PREFER_NOT_TO_ANSWER;
            default -> throw new IllegalArgumentException("pronoun preference is not supported: " + value);
        };
    }

    private static String canonicalFirstTimeHiring(String value) {
        String normalized = normalizeToken(value);
        if (normalized.contains("NAO") || normalized.contains("NUNCA") || normalized.contains("JAMAIS")) {
            return "NO";
        }
        if (normalized.contains("PRIMEIRA_VEZ")) {
            return "YES";
        }
        return switch (normalized) {
            case "YES", "SIM", "TRUE", "1" -> "YES";
            case "NO", "FALSE", "0" -> "NO";
            default -> throw new IllegalArgumentException("first-time hiring value is not supported: " + value);
        };
    }

    private String canonicalService(String value) {
        String normalized = normalizeToken(value);
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

    private static boolean acceptsTerms(String text) {
        if (text == null) return false;
        String normalized = normalizeEvidence(text);
        return !containsNegation(normalized)
                && normalized.matches(".*\\b(aceito|aceitar|concordo)\\b.*");
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
            default -> normalized.replace(' ', '_');
        };
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
