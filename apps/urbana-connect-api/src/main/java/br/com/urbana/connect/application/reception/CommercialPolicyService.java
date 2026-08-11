package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.FactConfidence;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic commercial checkpoints owned by Urbana Connect.  Hermes may
 * phrase the conversation, but it cannot bypass any method in this policy.
 */
public final class CommercialPolicyService {
    public static final String PREFER_NOT_TO_ANSWER = "PREFER_NOT_TO_ANSWER";
    private static final String PAYMENT_PROOF_PENDING_MESSAGE =
            "Recebi o comprovante. Agora ele aguarda validação humana; aviso assim que o pagamento for confirmado.";
    private static final List<String> MANDATORY_ICP = List.of(
            "PRONOUN_PREFERENCE", "FIRST_TIME_HIRING", "OCCUPATION");
    private static final List<String> ALLOWED_PAYMENT_METHODS = List.of("PIX", "CARD");

    private final Map<String, ServiceFixture> catalog;

    public CommercialPolicyService() {
        this(defaultCatalog().values());
    }

    public CommercialPolicyService(Collection<ServiceFixture> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            throw new IllegalArgumentException("catalog must not be empty");
        }
        Map<String, ServiceFixture> values = new LinkedHashMap<>();
        catalog.forEach(item -> values.put(normalizeService(item.serviceType()), item));
        this.catalog = Map.copyOf(values);
    }

    public List<String> mandatoryIcpFields() {
        return MANDATORY_ICP;
    }

    public List<String> missingIcpFields(Collection<CustomerFact> facts, Instant at) {
        return MANDATORY_ICP.stream()
                .filter(field -> !hasConfirmedCurrent(facts, field, at))
                .toList();
    }

    public boolean isIcpComplete(Collection<CustomerFact> facts, Instant at) {
        return missingIcpFields(facts, at).isEmpty();
    }

    public ServiceFixture service(String serviceType) {
        String normalized = normalizeService(serviceType);
        ServiceFixture service = catalog.get(normalized);
        if (service == null || !service.available()) {
            throw new IllegalArgumentException("service is not present in the approved catalog: " + serviceType);
        }
        return service;
    }

    public List<ServiceFixture> services() {
        return catalog.values().stream().toList();
    }

    public String serviceTypeForInteractiveReply(String replyId) {
        if (replyId == null || replyId.isBlank()) {
            throw new IllegalArgumentException("interactive service reply must not be blank");
        }
        String normalized = replyId.trim().toUpperCase(Locale.ROOT)
                .replaceFirst("^SERVICE[._-]", "");
        return service(normalized).serviceType();
    }

    public ReceptionConversation selectService(ReceptionConversation conversation, String serviceType, Instant now) {
        requireConversation(conversation);
        service(serviceType);
        return conversation.selectService(normalizeService(serviceType), Objects.requireNonNull(now, "now"));
    }

    public ReceptionConversation presentTerms(ReceptionConversation conversation,
                                              Collection<CustomerFact> facts, Instant now) {
        requireConversation(conversation);
        requireIcp(facts, now);
        if (conversation.selectedService() == null) {
            throw new IllegalStateException("service must be selected before terms");
        }
        return conversation.presentTerms(Objects.requireNonNull(now, "now"));
    }

    public ReceptionConversation acceptTerms(ReceptionConversation conversation, Instant now) {
        requireConversation(conversation);
        return conversation.acceptTerms(Objects.requireNonNull(now, "now"));
    }

    public ReceptionConversation preparePayment(ReceptionConversation conversation,
                                                Collection<CustomerFact> facts,
                                                String method, Instant now) {
        requireConversation(conversation);
        requireIcp(facts, now);
        if (conversation.selectedService() == null) {
            throw new IllegalStateException("service must be selected before payment");
        }
        service(conversation.selectedService());
        return conversation.preparePayment(true, normalizeMethod(method), Objects.requireNonNull(now, "now"));
    }

    /** A media/document proof can only move PREPARED to PROOF_RECEIVED. */
    public ReceptionConversation receivePaymentProof(ReceptionConversation conversation, Instant now) {
        requireConversation(conversation);
        if (conversation.paymentStatus() != PaymentStatus.PREPARED) {
            throw new IllegalStateException("payment proof requires payment status PREPARED");
        }
        ReceptionConversation evidence = conversation.receivePaymentProof(Objects.requireNonNull(now, "now"));
        if (evidence.paymentStatus() != PaymentStatus.PROOF_RECEIVED) {
            throw new IllegalStateException("payment evidence cannot confirm payment");
        }
        return evidence;
    }

    /**
     * Canonical entry point for image/document evidence. Approval remains a
     * separate backend-only transition and can never be inferred from media.
     */
    public ReceptionConversation receivePaymentEvidence(ReceptionConversation conversation, Instant now) {
        return receivePaymentProof(conversation, now);
    }

    /** Human-only approval checkpoint; not exposed as an Hermes/domain tool. */
    public ReceptionConversation approvePaymentProof(ReceptionConversation conversation, Instant now) {
        requireApprovalConversation(conversation);
        return conversation.confirmPayment(Objects.requireNonNull(now, "now"));
    }

    public String briefingFor(ReceptionConversation conversation) {
        requireReadableConversation(conversation);
        if (conversation.paymentStatus() != PaymentStatus.CONFIRMED) {
            throw new IllegalStateException("briefing requires human-confirmed payment");
        }
        return service(conversation.selectedService()).briefingText();
    }

    /**
     * Rejects an agent response that claims to release a briefing before the
     * backend-only approval transition. Other conversational text remains the
     * agent's responsibility and is reconciled by the normal output validator.
     */
    public AgentOutput reconcileOutput(AgentOutput candidate, ReceptionConversation conversation) {
        Objects.requireNonNull(candidate, "candidate");
        requireConversation(conversation);
        if (conversation.paymentStatus() == PaymentStatus.PROOF_RECEIVED) {
            return new AgentOutput(PAYMENT_PROOF_PENDING_MESSAGE, AgentNextAction.AWAIT_PAYMENT_APPROVAL);
        }
        String normalized = normalizeText(candidate.message());
        if (conversation.paymentStatus() != PaymentStatus.CONFIRMED) {
            boolean mentionsBriefing = normalized.contains("briefing");
            boolean claimsPaymentConfirmation = normalized.contains("pagamento")
                    && (normalized.contains("confirmad") || normalized.contains("aprova")
                    || normalized.contains("validado") || normalized.contains("compensado"));
            if (mentionsBriefing || (claimsPaymentConfirmation
                    && !explicitlyDeniesPaymentConfirmation(normalized))) {
                throw new IllegalArgumentException(
                        "payment confirmation and briefing cannot be claimed before human payment approval");
            }
        }
        return candidate;
    }

    public String termsUrl(String serviceType) {
        return service(serviceType).termsUrl();
    }

    public String paymentUrl(String serviceType) {
        return service(serviceType).paymentUrl();
    }

    private void requireIcp(Collection<CustomerFact> facts, Instant at) {
        if (!isIcpComplete(facts, at)) {
            throw new IllegalStateException("complete ICP is required before commercial checkpoint: "
                    + String.join(", ", missingIcpFields(facts, at)));
        }
    }

    private static boolean hasConfirmedCurrent(Collection<CustomerFact> facts, String type, Instant at) {
        if (facts == null || at == null) {
            return false;
        }
        return facts.stream().filter(Objects::nonNull)
                .anyMatch(fact -> type.equalsIgnoreCase(fact.type())
                        && fact.confidence() == FactConfidence.CONFIRMED
                        && fact.supersededBy() == null
                        && fact.isCurrentAt(at)
                        && !fact.value().isBlank());
    }

    private static void requireConversation(ReceptionConversation conversation) {
        Objects.requireNonNull(conversation, "conversation");
        if (conversation.mode() != ReceptionMode.AI) {
            throw new IllegalStateException("commercial checkpoints are disabled in human mode");
        }
    }

    private static void requireApprovalConversation(ReceptionConversation conversation) {
        Objects.requireNonNull(conversation, "conversation");
    }

    private static void requireReadableConversation(ReceptionConversation conversation) {
        Objects.requireNonNull(conversation, "conversation");
    }

    private static String normalizeService(String serviceType) {
        if (serviceType == null || serviceType.isBlank()) {
            throw new IllegalArgumentException("serviceType must not be blank");
        }
        return serviceType.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeText(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean explicitlyDeniesPaymentConfirmation(String normalized) {
        return normalized.contains("nao confirma")
                || normalized.contains("nao foi confirmado")
                || normalized.contains("nao esta confirmado")
                || normalized.contains("nao foi aprovado")
                || normalized.contains("nao aprova")
                || normalized.contains("submetido a aprovacao humana")
                || normalized.contains("submetida a aprovacao humana")
                || normalized.contains("aguarda aprovacao humana")
                || normalized.contains("aguardara aprovacao humana")
                || normalized.contains("enviado para aprovacao humana")
                || normalized.contains("enviada para aprovacao humana")
                || normalized.contains("aprovacao do pagamento sera feita por uma pessoa")
                || normalized.contains("aprovacao do pagamento sera feita exclusivamente por uma pessoa")
                || normalized.contains("aprovacao do pagamento sera realizada por uma pessoa")
                || normalized.contains("aprovacao do pagamento sera realizada exclusivamente por uma pessoa")
                || normalized.contains("aprovacao sera feita por uma pessoa")
                || normalized.contains("aprovacao sera feita exclusivamente por uma pessoa")
                || normalized.contains("aprovacao sera realizada por uma pessoa")
                || normalized.contains("aprovacao sera realizada exclusivamente por uma pessoa")
                || normalized.contains("aprovacao sera feita pela equipe")
                || normalized.contains("aprovacao sera feita exclusivamente pela equipe")
                || normalized.contains("confirmacao depende de aprovacao humana")
                || normalized.contains("encaminhado para aprovacao humana")
                || normalized.contains("sera analisado e aprovado exclusivamente pela equipe humana");
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("payment method must not be blank");
        }
        String normalized = method.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_PAYMENT_METHODS.contains(normalized)) {
            throw new IllegalArgumentException("payment method is not approved: " + method);
        }
        return normalized;
    }

    private static Map<String, ServiceFixture> defaultCatalog() {
        Map<String, ServiceFixture> values = new LinkedHashMap<>();
        values.put("DECOR", new ServiceFixture("DECOR", "Decor", "🛋️",
                "Solução de decoração para ambientes de até 20m².", new BigDecimal("400.00"),
                "https://fixtures.urbana.local/payment/decor", "https://fixtures.urbana.local/terms/decor",
                "https://fixtures.urbana.local/briefing/decor", "Briefing DECOR — fixture local.", true));
        values.put("DECOR_PINTURA", new ServiceFixture("DECOR_PINTURA", "Decor Pintura", "🎨",
                "Renovação com pintura, sem quebra-quebra.", new BigDecimal("250.00"),
                "https://fixtures.urbana.local/payment/decor-pintura", "https://fixtures.urbana.local/terms/decor-pintura",
                "https://fixtures.urbana.local/briefing/decor-pintura", "Briefing DECOR_PINTURA — fixture local.", true));
        values.put("DECOR_FACHADA", new ServiceFixture("DECOR_FACHADA", "Decor Fachada", "🏡",
                "Renovação de fachadas ou muros externos.", new BigDecimal("350.00"),
                "https://fixtures.urbana.local/payment/decor-fachada", "https://fixtures.urbana.local/terms/decor-fachada",
                "https://fixtures.urbana.local/briefing/decor-fachada", "Briefing DECOR_FACHADA — fixture local.", true));
        values.put("DECOR_REFORMA", new ServiceFixture("DECOR_REFORMA", "Decor Reforma", "🧱",
                "Reforma completa de um espaço interno.", new BigDecimal("450.00"),
                "https://fixtures.urbana.local/payment/decor-reforma", "https://fixtures.urbana.local/terms/decor-reforma",
                "https://fixtures.urbana.local/briefing/decor-reforma", "Briefing DECOR_REFORMA — fixture local.", true));
        return values;
    }

    public record ServiceFixture(String serviceType, String name, String emoji, String description,
                                 BigDecimal price, String paymentUrl, String termsUrl,
                                 String briefingUrl, String briefingText, boolean available) {
        public ServiceFixture {
            if (serviceType == null || serviceType.isBlank() || name == null || name.isBlank()
                    || description == null || description.isBlank() || price == null || price.signum() < 0
                    || paymentUrl == null || termsUrl == null || briefingUrl == null
                    || briefingText == null || briefingText.isBlank()) {
                throw new IllegalArgumentException("fixture service fields are incomplete");
            }
        }
    }
}
