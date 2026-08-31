package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.CustomerFactType;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.servicecatalog.model.AreaRule;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic commercial checkpoints owned by Urbana Connect.  Hermes may
 * phrase the conversation, but it cannot bypass any method in this policy.
 */
public final class CommercialPolicyService {
    public static final String PREFER_NOT_TO_ANSWER = "PREFER_NOT_TO_ANSWER";
    private static final String PAYMENT_PROOF_PENDING_MESSAGE =
            "Recebi o comprovante. Agora ele aguarda validação humana; aviso assim que o pagamento for confirmado.";
    private static final String ACCEPTANCE_WORD = "aceito";
    private static final String AGREEMENT_WORD = "concordo";
    private static final List<String> ACCEPTANCE_WORDS = List.of(ACCEPTANCE_WORD, AGREEMENT_WORD);
    private static final List<String> TERMS_REVIEW_VERBS =
            List.of("ler", "analisar", "revisar", "avaliar", "verificar", "refletir", "pensar");
    private static final List<String> REVIEW_PREPOSITIONS = List.of("em", "com", "para");
    private static final List<String> DEFERRED_REVIEW_MODALS =
            List.of("vou", "irei", "pretendo", "quero", "preciso");
    private static final String IMMEDIATE_REVIEW_SEPARATORS = ",:;";
    private static final String ACCEPTANCE_SEPARATORS = "!,. ";
    private static final String IMMEDIATE_TARGET_SEPARATORS = ",;:.! ";
    private static final List<String> MANDATORY_ICP = CustomerFactType.icpFieldNames();
    private static final List<String> ALLOWED_PAYMENT_METHODS = List.of("PIX", "CARD");

    private final Map<String, ServiceFixture> catalog;

    public CommercialPolicyService() {
        this(ServiceCatalogItem.canonicalCatalog().stream()
                .map(ServiceFixture::from)
                .toList());
    }

    public CommercialPolicyService(Collection<ServiceFixture> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            throw new IllegalArgumentException("catalog must not be empty");
        }
        Map<String, ServiceFixture> values = new LinkedHashMap<>();
        catalog.forEach(item -> values.put(normalizeService(item.serviceType()), item));
        this.catalog = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public List<String> mandatoryIcpFields() {
        return MANDATORY_ICP;
    }

    public List<String> missingIcpFields(Collection<CustomerFact> facts, Instant at) {
        return MANDATORY_ICP.stream()
                .filter(field -> latestVersion(facts, field, at)
                        .map(fact -> fact.isReusableAt(at))
                        .map(complete -> !complete)
                        .orElse(true))
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
        String normalized = normalizeService(serviceType);
        service(normalized);
        if (normalized.equalsIgnoreCase(conversation.selectedService())) {
            return conversation;
        }
        return conversation.selectService(normalized, Objects.requireNonNull(now, "now"));
    }

    public ReceptionConversation presentTerms(ReceptionConversation conversation,
                                              Collection<CustomerFact> facts, Instant now) {
        requireConversation(conversation);
        if (conversation.selectedService() == null) {
            throw new IllegalStateException("service must be selected before terms");
        }
        service(conversation.selectedService());
        if (conversation.termsStatus() == br.com.urbana.connect.domain.reception.model.TermsStatus.ACCEPTED
                && conversation.activeTermsConsentId() == null) {
            return conversation.reopenTermsForAudit(Objects.requireNonNull(now, "now"));
        }
        if (conversation.termsStatus() != br.com.urbana.connect.domain.reception.model.TermsStatus.NOT_PRESENTED
                && conversation.termsStatus() != br.com.urbana.connect.domain.reception.model.TermsStatus.DECLINED) {
            return conversation;
        }
        return conversation.presentTerms(Objects.requireNonNull(now, "now"));
    }

    public ReceptionConversation acceptTerms(ReceptionConversation conversation, Instant now) {
        requireConversation(conversation);
        if (conversation.termsStatus() == br.com.urbana.connect.domain.reception.model.TermsStatus.ACCEPTED) {
            return conversation;
        }
        return conversation.acceptTerms(Objects.requireNonNull(now, "now"));
    }

    public ReceptionConversation acceptTerms(ReceptionConversation conversation,
                                              String acceptanceText,
                                              Instant now) {
        if (!isExplicitTermsAcceptance(acceptanceText)) {
            throw new IllegalArgumentException("terms acceptance must be clear and textual");
        }
        return acceptTerms(conversation, now);
    }

    public boolean isExplicitTermsAcceptance(String acceptanceText) {
        if (acceptanceText == null || acceptanceText.isBlank()) {
            return false;
        }
        String normalized = normalizeText(acceptanceText);
        if (normalized.contains("nao") || normalized.contains("recuso") || normalized.contains("discordo")) {
            return false;
        }
        if (hasImmediateReviewRequest(normalized)
                || hasDeferredReviewRequest(normalized)
                || hasUncertainAcceptance(normalized)
                || hasAcceptanceFollowedBy(normalized, "depois", true)) {
            return false;
        }
        return isBareAcceptance(normalized)
                || isAcceptanceWithPaymentMethod(normalized)
                || hasAcceptanceFollowedBy(normalized, "termos", false)
                || hasAcceptancePhraseFollowedBy(normalized, "estou de acordo", "termos");
    }

    private static boolean hasImmediateReviewRequest(String normalized) {
        return ACCEPTANCE_WORDS.stream()
                .anyMatch(acceptance -> hasImmediateReviewRequest(normalized, acceptance));
    }

    private static boolean hasImmediateReviewRequest(String normalized, String acceptance) {
        int start = indexOfWord(normalized, acceptance, 0);
        while (start >= 0) {
            int reviewStart = immediateReviewStart(normalized, start, acceptance);
            if (containsReviewVerbAt(normalized, reviewStart)) {
                return true;
            }
            start = indexOfWord(normalized, acceptance, start + acceptance.length());
        }
        return false;
    }

    private static int immediateReviewStart(String normalized, int acceptanceIndex, String acceptance) {
        int cursor = skipWhitespace(normalized, acceptanceIndex + acceptance.length());
        cursor = skipSingleSeparator(normalized, cursor, IMMEDIATE_REVIEW_SEPARATORS);
        return skipReviewPreposition(normalized, cursor);
    }

    private static int skipReviewPreposition(String normalized, int start) {
        for (String preposition : REVIEW_PREPOSITIONS) {
            int afterPreposition = wordEnd(normalized, start, preposition);
            if (afterPreposition >= 0 && hasWhitespaceAfter(normalized, afterPreposition)) {
                return skipWhitespace(normalized, afterPreposition);
            }
        }
        return start;
    }

    private static boolean hasDeferredReviewRequest(String normalized) {
        for (String acceptance : ACCEPTANCE_WORDS) {
            int acceptanceIndex = indexOfWord(normalized, acceptance, 0);
            while (acceptanceIndex >= 0) {
                for (String modal : DEFERRED_REVIEW_MODALS) {
                    int modalIndex = indexOfWord(normalized, modal, acceptanceIndex + acceptance.length());
                    if (modalIndex >= 0 && containsReviewVerbAfter(normalized, modalIndex + modal.length())) {
                        return true;
                    }
                }
                acceptanceIndex = indexOfWord(normalized, acceptance, acceptanceIndex + acceptance.length());
            }
        }
        return false;
    }

    private static boolean hasUncertainAcceptance(String normalized) {
        for (String acceptance : ACCEPTANCE_WORDS) {
            int acceptanceIndex = indexOfWord(normalized, acceptance, 0);
            if (acceptanceIndex >= 0
                    && indexOfWord(normalized, "talvez", acceptanceIndex + acceptance.length()) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBareAcceptance(String normalized) {
        if (!normalized.startsWith(ACCEPTANCE_WORD)) {
            return false;
        }
        String suffix = normalized.substring(ACCEPTANCE_WORD.length());
        return suffix.chars().allMatch(character -> ACCEPTANCE_SEPARATORS.indexOf(character) >= 0);
    }

    private static boolean isAcceptanceWithPaymentMethod(String normalized) {
        if (!normalized.startsWith(ACCEPTANCE_WORD)
                || normalized.length() == ACCEPTANCE_WORD.length()) {
            return false;
        }
        char separator = normalized.charAt(ACCEPTANCE_WORD.length());
        if (ACCEPTANCE_SEPARATORS.indexOf(separator) < 0) {
            return false;
        }
        return indexOfAnyWord(normalized, List.of("pix", "cartao", "credito"), ACCEPTANCE_WORD.length()) >= 0;
    }

    private static boolean hasAcceptanceFollowedBy(String normalized, String target, boolean immediate) {
        return ACCEPTANCE_WORDS.stream()
                .anyMatch(acceptance -> hasAcceptanceFollowedBy(normalized, acceptance, target, immediate));
    }

    private static boolean hasAcceptanceFollowedBy(String normalized, String acceptance,
                                                     String target, boolean immediate) {
        int acceptanceIndex = indexOfWord(normalized, acceptance, 0);
        while (acceptanceIndex >= 0) {
            if (targetFollowsAcceptance(normalized, acceptanceIndex, acceptance, target, immediate)) {
                return true;
            }
            acceptanceIndex = indexOfWord(normalized, acceptance,
                    acceptanceIndex + acceptance.length());
        }
        return false;
    }

    private static boolean targetFollowsAcceptance(String normalized, int acceptanceIndex,
                                                    String acceptance, String target, boolean immediate) {
        int from = acceptanceIndex + acceptance.length();
        if (immediate) {
            from = skipCharacters(normalized, from, IMMEDIATE_TARGET_SEPARATORS);
            return wordEnd(normalized, from, target) >= 0;
        }
        return indexOfWord(normalized, target, from) >= 0;
    }

    private static boolean hasAcceptancePhraseFollowedBy(String normalized, String phrase, String target) {
        int phraseIndex = indexOfPhrase(normalized, phrase, 0);
        return phraseIndex >= 0 && indexOfWord(normalized, target, phraseIndex + phrase.length()) >= 0;
    }

    private static boolean containsReviewVerbAt(String normalized, int start) {
        for (String verb : TERMS_REVIEW_VERBS) {
            if (wordEnd(normalized, start, verb) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsReviewVerbAfter(String normalized, int start) {
        for (String verb : TERMS_REVIEW_VERBS) {
            int index = indexOfWord(normalized, verb, start);
            if (index >= 0 && hasWhitespaceBetween(normalized, start, index)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfAnyWord(String normalized, List<String> values, int fromIndex) {
        return values.stream()
                .mapToInt(value -> indexOfWord(normalized, value, fromIndex))
                .filter(index -> index >= 0)
                .min()
                .orElse(-1);
    }

    private static int indexOfWord(String value, String word, int fromIndex) {
        int index = value.indexOf(word, fromIndex);
        while (index >= 0) {
            if (isWordBoundary(value, index - 1) && isWordBoundary(value, index + word.length())) {
                return index;
            }
            index = value.indexOf(word, index + word.length());
        }
        return -1;
    }

    private static int indexOfPhrase(String value, String phrase, int fromIndex) {
        int index = value.indexOf(phrase, fromIndex);
        while (index >= 0) {
            if (isWordBoundary(value, index - 1)
                    && isWordBoundary(value, index + phrase.length())) {
                return index;
            }
            index = value.indexOf(phrase, index + phrase.length());
        }
        return -1;
    }

    private static int wordEnd(String value, int start, String word) {
        return indexOfWord(value, word, start) == start ? start + word.length() : -1;
    }

    private static boolean hasWhitespaceAfter(String value, int start) {
        return start < value.length() && Character.isWhitespace(value.charAt(start));
    }

    private static int skipWhitespace(String value, int start) {
        int cursor = start;
        while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int skipSingleSeparator(String value, int start, String separators) {
        if (start < value.length() && separators.indexOf(value.charAt(start)) >= 0) {
            return skipWhitespace(value, start + 1);
        }
        return start;
    }

    private static int skipCharacters(String value, int start, String characters) {
        int cursor = start;
        while (cursor < value.length() && characters.indexOf(value.charAt(cursor)) >= 0) {
            cursor++;
        }
        return cursor;
    }

    private static boolean hasWhitespaceBetween(String value, int start, int end) {
        return start < end && value.substring(start, end).chars().allMatch(Character::isWhitespace);
    }

    private static boolean isWordBoundary(String value, int index) {
        return index < 0 || index >= value.length() || !Character.isLetterOrDigit(value.charAt(index));
    }

    public ReceptionConversation preparePayment(ReceptionConversation conversation,
                                                Collection<CustomerFact> facts,
                                                String method, Instant now) {
        requireConversation(conversation);
        if (conversation.selectedService() == null) {
            throw new IllegalStateException("service must be selected before payment");
        }
        service(conversation.selectedService());
        if (conversation.paymentStatus() == PaymentStatus.PREPARED
                || conversation.paymentStatus() == PaymentStatus.PROOF_RECEIVED
                || conversation.paymentStatus() == PaymentStatus.CONFIRMED) {
            return conversation;
        }
        String normalizedMethod = normalizeMethod(method);
        return conversation.preparePayment(false, normalizedMethod, Objects.requireNonNull(now, "now"));
    }

    /** A media/document proof can only move PREPARED to PROOF_RECEIVED. */
    public ReceptionConversation receivePaymentProof(ReceptionConversation conversation, Instant now) {
        requireConversation(conversation);
        if (conversation.paymentStatus() == PaymentStatus.PROOF_RECEIVED
                || conversation.paymentStatus() == PaymentStatus.CONFIRMED) {
            return conversation;
        }
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
        if (conversation.paymentStatus() == PaymentStatus.CONFIRMED) {
            return conversation;
        }
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
        if (candidate.nextAction() == AgentNextAction.AWAIT_PAYMENT_APPROVAL
                && conversation.paymentStatus() != PaymentStatus.PROOF_RECEIVED) {
            throw new IllegalArgumentException("payment approval cannot be awaited without proof");
        }
        if (candidate.nextAction() == AgentNextAction.AWAIT_PAYMENT_PROOF
                && conversation.paymentStatus() != PaymentStatus.PREPARED) {
            throw new IllegalArgumentException("payment proof cannot be awaited before payment preparation");
        }
        if (conversation.paymentStatus() == PaymentStatus.PROOF_RECEIVED) {
            return new AgentOutput(PAYMENT_PROOF_PENDING_MESSAGE, AgentNextAction.AWAIT_PAYMENT_APPROVAL);
        }
        String normalized = normalizeText(candidate.message());
        if (conversation.paymentStatus() == PaymentStatus.PREPARED
                && (!normalized.contains("1 servico para cada ambiente") || !normalized.contains("comprovante"))) {
            throw new IllegalArgumentException("prepared payment output must include quantity per environment and proof guidance");
        }
        if (conversation.paymentStatus() != PaymentStatus.CONFIRMED) {
            boolean briefingReleaseClaim = claimsBriefingRelease(normalized)
                    && !briefingIsExplicitlyDeferred(normalized);
            boolean prematurePaymentInstruction = conversation.paymentStatus() != PaymentStatus.PREPARED
                    && conversation.paymentStatus() != PaymentStatus.PROOF_RECEIVED
                    && claimsPaymentInstruction(normalized);
            boolean claimsPaymentConfirmation = claimsPaymentConfirmation(normalized);
            if (briefingReleaseClaim || prematurePaymentInstruction || (claimsPaymentConfirmation
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

    public String briefingUrl(String serviceType) {
        return service(serviceType).briefingUrl();
    }

    public AreaRule areaRule(String serviceType) {
        return service(serviceType).areaRule();
    }

    public boolean isAreaWithinCatalog(String serviceType, BigDecimal squareMeters) {
        return service(serviceType).areaRule().accepts(squareMeters);
    }

    public boolean requiresArchitectAreaReview(String serviceType, BigDecimal squareMeters) {
        return service(serviceType).areaRule().requiresArchitectReview(squareMeters);
    }

    private static Optional<CustomerFact> latestVersion(Collection<CustomerFact> facts, String type, Instant at) {
        if (facts == null || at == null) {
            return Optional.empty();
        }
        return facts.stream()
                .filter(Objects::nonNull)
                .filter(fact -> type.equalsIgnoreCase(fact.type()))
                .filter(fact -> fact.supersededBy() == null)
                .filter(fact -> !fact.validFrom().isAfter(at))
                .max(Comparator.comparing(CustomerFact::validFrom).thenComparing(CustomerFact::id));
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
        String normalized = serviceType.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "DECOR".equals(normalized) ? "DECOR_INTERIORES" : normalized;
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
                || normalized.matches(".*\\b(?:nenhum|nao)\\s+pagamento\\b.*\\b(?:confirmad|aprovad|valid|compensad).*" )
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

    private static boolean claimsPaymentConfirmation(String normalized) {
        return normalized.matches(".*\\bpagamento\\b\\s+(?:foi\\s+)?(?:confirmad|aprovad|validad|compensad).*")
                || normalized.matches(".*\\b(?:confirmad|aprovad|validad|compensad)\\b\\s+(?:o\\s+)?pagamento\\b.*");
    }

    private static boolean claimsBriefingRelease(String normalized) {
        boolean directIntroduction = normalized.matches(
                ".*\\b(?:aqui esta|segue)\\s+(?:o\\s+)?briefing\\b.*");
        boolean directReadyState = normalized.matches(
                ".*\\bbriefing\\b.{0,24}\\b(?:esta|foi|ja esta|ja foi)\\s+"
                        + "(?:pronto|liberad|disponibilizad|disponivel|enviad)\\b.*");
        boolean directReadyLabel = normalized.matches(
                ".*\\bbriefing\\s+(?:ja\\s+)?(?:pronto|liberad|disponibilizad|disponivel|enviad)\\b.*");
        boolean directAccess = normalized.matches(
                ".*\\b(?:acesse|abra|abrir|clique)\\b.{0,40}\\bbriefing\\b.*");
        return directIntroduction || directReadyState || directReadyLabel || directAccess;
    }

    private static boolean claimsPaymentInstruction(String normalized) {
        return normalized.matches(".*https?://\\S*(?:payment|pagamento|pix)\\S*.*")
                || normalized.matches(".*\\b(?:link|url|chave)\\b.{0,40}\\b(?:de\\s+)?(?:pagamento|pix)\\b.*")
                || normalized.matches(".*\\b(?:pagamento|pagar|pague)\\b.{0,40}\\b(?:link|url|chave)\\b.*");
    }

    private static boolean briefingIsExplicitlyDeferred(String normalized) {
        return normalized.contains("apos pagamento")
                || normalized.contains("apos o pagamento")
                || normalized.contains("apos a confirmacao")
                || normalized.contains("apos confirmacao")
                || normalized.contains("depois do pagamento")
                || normalized.contains("depois de aceitar")
                || normalized.contains("depois do aceite")
                || normalized.contains("apos aceitar")
                || normalized.contains("apos o aceite")
                || normalized.contains("quando o pagamento")
                || normalized.contains("assim que o pagamento");
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

    public record ServiceFixture(String serviceType, String name, String emoji, String description,
                                 BigDecimal price, String paymentUrl, String termsUrl,
                                 String briefingUrl, String briefingText, boolean available,
                                 AreaRule areaRule, String scope, List<String> deliverables,
                                 List<String> process, List<String> responsibilities,
                                 List<String> exclusions, String support) {
        public ServiceFixture(String serviceType, String name, String emoji, String description,
                              BigDecimal price, String paymentUrl, String termsUrl,
                              String briefingUrl, String briefingText, boolean available) {
            this(serviceType, name, emoji, description, price, paymentUrl, termsUrl, briefingUrl,
                    briefingText, available, AreaRule.UNLIMITED_BY_CATALOG, description,
                    List.of(), List.of(), List.of(), List.of(), "legacy adapter");
        }

        private static ServiceFixture from(ServiceCatalogItem item) {
            return new ServiceFixture(
                    item.type().name(),
                    item.name(),
                    item.emoji(),
                    item.scope(),
                    item.price(),
                    item.paymentResource(),
                    item.termsResource(),
                    item.briefingResource(),
                    "Briefing " + item.type().name() + " — " + item.scope(),
                    item.available(),
                    item.areaRule(),
                    item.scope(),
                    item.deliverables(),
                    item.process(),
                    item.responsibilities(),
                    item.exclusions(),
                    item.support());
        }

        public ServiceFixture {
            if (serviceType == null || serviceType.isBlank() || name == null || name.isBlank()
                    || description == null || description.isBlank() || price == null || price.signum() < 0
                    || paymentUrl == null || termsUrl == null || briefingUrl == null
                    || briefingText == null || briefingText.isBlank() || areaRule == null
                    || scope == null || scope.isBlank() || support == null || support.isBlank()) {
                throw new IllegalArgumentException("catalog service fields are incomplete");
            }
            deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
            process = process == null ? List.of() : List.copyOf(process);
            responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
            exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
        }
    }
}
