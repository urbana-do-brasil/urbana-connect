package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Final application-side guard for the conversational part of a Hermes turn.
 *
 * <p>Hermes may choose the wording and the next action, but this policy only
 * accepts wording grounded in the approved fixture catalog and an action
 * compatible with the authoritative operational state. It never changes
 * customer facts; corrections remain versioned by the existing domain tool
 * and are read here through their current, non-superseded version.</p>
 */
public final class ReceptionResponsePolicy {
    public static final String UNSUPPORTED_COMMERCIAL_CLAIM = "unsupported_commercial_claim";
    public static final String STALE_FACT_REFERENCE = "stale_fact_reference";
    public static final String INVALID_NEXT_ACTION = "invalid_next_action";
    public static final String SAFE_FALLBACK_MESSAGE =
            "Não consigo confirmar essa informação com segurança. "
                    + "Posso esclarecer sua necessidade com base nas opções aprovadas?";

    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "R\\$\\s*([0-9]{1,3}(?:[.\\s][0-9]{3})*(?:,[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern REAIS_PATTERN = Pattern.compile(
            "\\b([0-9]{1,3}(?:[.\\s][0-9]{3})*(?:,[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)\\s+reais?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SERVICE_REFERENCE_PATTERN = Pattern.compile(
            "\\b(?:servico|opcao|pacote|projeto|plano|modalidade)\\s+"
                    + "(?:de\\s+|para\\s+)?([\\p{L}][\\p{L}\\d_-]*(?:\\s+[\\p{L}\\d_-]+){0,2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DEADLINE_PATTERN = Pattern.compile(
            "\\b(?:prazo(?:\\s+de)?|entrega|entregar|fica\\s+pronto|conclusao)\\s+"
                    + "(?:e\\s+|eh\\s+|em\\s+|de\\s+|ate\\s+)?\\d+\\s*"
                    + "(?:dias?|semanas?|mes(?:es)?|horas?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern RELATIVE_DEADLINE_PATTERN = Pattern.compile(
            "\\b(?:em|ate)\\s+\\d+\\s*(?:dias?|semanas?|mes(?:es)?|horas?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DISCOUNT_PATTERN = Pattern.compile(
            "\\b(?:\\d+\\s*%\\s*(?:de\\s*)?desconto|desconto\\s+(?:de\\s*)?\\d+"
                    + "|promocao|cupom|brinde|bonus|gratis|gratuito|sem\\s+custo"
                    + "|preco\\s+promocional|condicao\\s+especial)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AVAILABILITY_PATTERN = Pattern.compile(
            "\\b(?:disponibilidade|agenda\\s+aberta|vagas?|disponivel)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern IMMEDIATE_AVAILABILITY_PATTERN = Pattern.compile(
            "\\b(?:disponivel|disponibilidade)\\s+"
                    + "(?:imediat[ao]|agora|hoje|amanha|garantid[ao]|confirmad[ao])\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern UNSUPPORTED_CONDITION_PATTERN = Pattern.compile(
            "\\b(?:condicao\\s+(?:especial|comercial)|garantia|parcelamento|parcelad[oa]"
                    + "|juros|entrada|sinal|consultoria\\s+extra|servico\\s+extra)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Set<String> GENERIC_SERVICE_WORDS = Set.of(
            "adequado", "aprovado", "aprovada", "aprovadas", "certa", "certo", "disponivel", "disponibilidade", "ideal",
            "indicado", "melhor", "nosso", "nossa", "opcoes", "opcao", "qualquer",
            "antes", "parecido", "completo", "completa", "e", "que", "qual", "uma", "um",
            "pela", "primeira", "vez", "da", "desse", "tipo", "oferece", "escolhido", "escolhida",
            "selecionado", "selecionada");

    private final CommercialPolicyService commercialPolicy;
    private final Clock clock;

    public ReceptionResponsePolicy() {
        this(new CommercialPolicyService(), Clock.systemUTC());
    }

    public ReceptionResponsePolicy(CommercialPolicyService commercialPolicy) {
        this(commercialPolicy, Clock.systemUTC());
    }

    public ReceptionResponsePolicy(CommercialPolicyService commercialPolicy, Clock clock) {
        this.commercialPolicy = Objects.requireNonNull(commercialPolicy, "commercialPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Returns the current version of a fact while leaving both current and
     * superseded versions available to the caller for audit.
     */
    public Optional<CustomerFact> currentFact(Collection<CustomerFact> facts, String type, Instant at) {
        if (facts == null || type == null || type.isBlank() || at == null) {
            return Optional.empty();
        }
        return facts.stream()
                .filter(Objects::nonNull)
                .filter(fact -> type.equalsIgnoreCase(fact.type()))
                .filter(fact -> fact.supersededBy() == null && fact.isCurrentAt(at))
                .max(Comparator.comparing(CustomerFact::validFrom));
    }

    /**
     * Validates the untrusted agent output and returns a publication-safe
     * result. The accepted output is returned unchanged, including its
     * independent conversational message and next action.
     */
    public ValidationResult validate(AgentOutput candidate,
                                     ReceptionConversation conversation,
                                     Collection<CustomerFact> facts,
                                     Instant at) {
        Instant effectiveAt = at == null ? clock.instant() : at;
        List<CustomerFact> safeFacts = facts == null ? List.of() : facts.stream()
                .filter(Objects::nonNull)
                .toList();

        if (candidate == null) {
            return rejected("missing_agent_output");
        }
        if (conversation == null) {
            return rejected("missing_conversation");
        }

        String actionViolation = actionViolation(candidate, conversation);
        if (actionViolation != null) {
            return rejected(actionViolation);
        }
        if (referencesSupersededFact(candidate.message(), safeFacts, effectiveAt)) {
            return rejected(STALE_FACT_REFERENCE);
        }
        if (containsUnsupportedCommercialClaim(candidate.message())) {
            return rejected(UNSUPPORTED_COMMERCIAL_CLAIM);
        }
        return ValidationResult.accepted(candidate);
    }

    public ValidationResult validate(AgentOutput candidate, ReceptionConversation conversation) {
        return validate(candidate, conversation, List.of(), clock.instant());
    }

    /** Convenience boundary for callers that only need the publishable output. */
    public AgentOutput reconcile(AgentOutput candidate,
                                 ReceptionConversation conversation,
                                 Collection<CustomerFact> facts,
                                 Instant at) {
        return validate(candidate, conversation, facts, at).output();
    }

    public AgentOutput safeFallback(String reason) {
        return new AgentOutput(SAFE_FALLBACK_MESSAGE, AgentNextAction.AWAIT_CUSTOMER);
    }

    private ValidationResult rejected(String reason) {
        return ValidationResult.rejected(reason, safeFallback(reason));
    }

    private String actionViolation(AgentOutput candidate, ReceptionConversation conversation) {
        return switch (candidate.nextAction()) {
            case AWAIT_PAYMENT_PROOF -> conversation.paymentStatus() == PaymentStatus.PREPARED
                    ? null : INVALID_NEXT_ACTION;
            case AWAIT_PAYMENT_APPROVAL -> conversation.paymentStatus() == PaymentStatus.PROOF_RECEIVED
                    ? null : INVALID_NEXT_ACTION;
            default -> null;
        };
    }

    private boolean referencesSupersededFact(String message,
                                             Collection<CustomerFact> facts,
                                             Instant at) {
        String normalizedMessage = normalize(message);
        for (CustomerFact previous : facts) {
            if (previous.supersededBy() == null || !meaningfulFactValue(previous.value())) {
                continue;
            }
            Optional<CustomerFact> replacement = facts.stream()
                    .filter(Objects::nonNull)
                    .filter(fact -> previous.supersededBy().equals(fact.id()))
                    .filter(fact -> fact.isCurrentAt(at) && fact.supersededBy() == null)
                    .findFirst();
            if (replacement.isEmpty()) {
                continue;
            }
            if (containsTerm(normalizedMessage, previous.value())
                    && !containsTerm(normalizedMessage, replacement.get().value())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsUnsupportedCommercialClaim(String message) {
        return mentionsUnknownService(message)
                || mentionsUnapprovedPrice(message)
                || mentionsUnsupportedDeadline(message)
                || mentionsUnsupportedDiscount(message)
                || mentionsUnsupportedAvailability(message)
                || mentionsUnsupportedCondition(message);
    }

    private boolean mentionsUnknownService(String message) {
        Set<String> approvedAliases = approvedServiceAliases();
        Matcher matcher = SERVICE_REFERENCE_PATTERN.matcher(normalize(message));
        while (matcher.find()) {
            String phrase = matcher.group(1).trim();
            String firstWord = phrase.split("\\s+")[0];
            if (GENERIC_SERVICE_WORDS.contains(firstWord)) {
                continue;
            }
            boolean approved = approvedAliases.stream()
                    .anyMatch(alias -> phrase.equals(alias) || phrase.startsWith(alias + " ")
                            || phrase.contains(" " + alias + " "));
            if (!approved) {
                return true;
            }
        }
        return false;
    }

    private Set<String> approvedServiceAliases() {
        Set<String> aliases = new LinkedHashSet<>();
        for (CommercialPolicyService.ServiceFixture service : commercialPolicy.services()) {
            if (!service.available()) {
                continue;
            }
            aliases.add(normalize(service.serviceType()));
            aliases.add(normalize(service.name()));
            String normalizedType = normalize(service.serviceType());
            for (String part : normalizedType.split(" ")) {
                if (part.length() >= 4) {
                    aliases.add(part);
                }
            }
            if ("decor".equals(normalizedType)) {
                aliases.add("decoracao");
            }
        }
        return aliases;
    }

    private boolean mentionsUnapprovedPrice(String message) {
        String normalized = normalize(message);
        List<PriceMention> prices = new ArrayList<>();
        collectPriceMentions(PRICE_PATTERN.matcher(normalized), prices);
        collectPriceMentions(REAIS_PATTERN.matcher(normalized), prices);
        if (prices.isEmpty()) {
            return false;
        }

        List<ServiceMention> services = approvedPriceServiceMentions(normalized);
        for (PriceMention price : prices) {
            Optional<ServiceMention> associatedService = nearestServiceInSentence(normalized, price, services);
            if (price.amount() == null || associatedService.isEmpty()) {
                return true;
            }
            BigDecimal approvedPrice = associatedService.get().service().price();
            if (approvedPrice == null || approvedPrice.compareTo(price.amount()) != 0) {
                return true;
            }
        }
        return false;
    }

    private void collectPriceMentions(Matcher matcher, List<PriceMention> prices) {
        while (matcher.find()) {
            BigDecimal amount = parseBrazilianAmount(matcher.group(1));
            if (amount == null) {
                prices.add(new PriceMention(null, matcher.start(), matcher.end()));
            } else {
                prices.add(new PriceMention(amount, matcher.start(), matcher.end()));
            }
        }
    }

    private List<ServiceMention> approvedPriceServiceMentions(String normalizedMessage) {
        List<ServiceMention> mentions = new ArrayList<>();
        for (CommercialPolicyService.ServiceFixture service : commercialPolicy.services()) {
            if (!service.available()) {
                continue;
            }
            Set<String> canonicalAliases = new LinkedHashSet<>();
            canonicalAliases.add(normalize(service.serviceType()));
            canonicalAliases.add(normalize(service.name()));
            for (String alias : canonicalAliases) {
                int fromIndex = 0;
                while (fromIndex < normalizedMessage.length()) {
                    int start = normalizedMessage.indexOf(alias, fromIndex);
                    if (start < 0) {
                        break;
                    }
                    int end = start + alias.length();
                    if (isTermBoundary(normalizedMessage, start, end)) {
                        mentions.add(new ServiceMention(service, start, end));
                    }
                    fromIndex = end;
                }
            }
        }
        return mentions.stream()
                .filter(mention -> mentions.stream().noneMatch(other ->
                        other != mention
                                && other.start() <= mention.start()
                                && other.end() >= mention.end()
                                && (other.start() < mention.start() || other.end() > mention.end())))
                .toList();
    }

    private Optional<ServiceMention> nearestServiceInSentence(String normalizedMessage,
                                                               PriceMention price,
                                                               List<ServiceMention> services) {
        int sentenceStart = sentenceStart(normalizedMessage, price.start());
        int sentenceEnd = sentenceEnd(normalizedMessage, price.end());
        Optional<ServiceMention> sameSentence = services.stream()
                .filter(service -> service.start() >= sentenceStart && service.end() <= sentenceEnd)
                .min(Comparator.comparingInt(service -> distance(service, price)));
        if (sameSentence.isPresent()) {
            return sameSentence;
        }
        // Hermes commonly places the approved service in one sentence and its
        // approved price in the next. Keep the fallback conservative: when
        // more than one service is mentioned, an unscoped price remains unsafe.
        return services.size() == 1 ? Optional.of(services.getFirst()) : Optional.empty();
    }

    private int sentenceStart(String text, int position) {
        int start = 0;
        for (char delimiter : new char[]{'.', '!', '?', '\n'}) {
            start = Math.max(start, text.lastIndexOf(delimiter, position - 1) + 1);
        }
        return start;
    }

    private int sentenceEnd(String text, int position) {
        int end = text.length();
        for (char delimiter : new char[]{'.', '!', '?', '\n'}) {
            int next = text.indexOf(delimiter, position);
            if (next >= 0) {
                end = Math.min(end, next);
            }
        }
        return end;
    }

    private int distance(ServiceMention service, PriceMention price) {
        if (service.end() <= price.start()) {
            return price.start() - service.end();
        }
        if (price.end() <= service.start()) {
            return service.start() - price.end();
        }
        return 0;
    }

    private static boolean isTermBoundary(String text, int start, int end) {
        return (start == 0 || !isTermCharacter(text.charAt(start - 1)))
                && (end == text.length() || !isTermCharacter(text.charAt(end)));
    }

    private static boolean isTermCharacter(char character) {
        return Character.isLetterOrDigit(character);
    }

    private BigDecimal parseBrazilianAmount(String raw) {
        try {
            String value = raw.replace(" ", "");
            if (value.contains(",")) {
                value = value.replace(".", "").replace(',', '.');
            } else if (value.indexOf('.') != value.lastIndexOf('.')) {
                value = value.replace(".", "");
            } else if (value.contains(".") && value.substring(value.indexOf('.') + 1).length() == 3) {
                value = value.replace(".", "");
            }
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean mentionsUnsupportedDeadline(String message) {
        String normalized = normalize(message);
        return DEADLINE_PATTERN.matcher(normalized).find()
                || RELATIVE_DEADLINE_PATTERN.matcher(normalized).find();
    }

    private boolean mentionsUnsupportedDiscount(String message) {
        return DISCOUNT_PATTERN.matcher(normalize(message)).find();
    }

    private boolean mentionsUnsupportedAvailability(String message) {
        String normalized = normalize(message);
        if (!AVAILABILITY_PATTERN.matcher(normalized).find()) {
            return false;
        }
        if (normalized.contains("nao disponivel") || normalized.contains("indisponivel")
                || normalized.contains("disponibilidade nao") || normalized.contains("sem disponibilidade")) {
            return false;
        }
        if (IMMEDIATE_AVAILABILITY_PATTERN.matcher(normalized).find()
                || normalized.contains("agenda aberta") || normalized.matches(".*\\bvagas?\\b.*")) {
            return true;
        }
        return !mentionsApprovedService(message);
    }

    private boolean mentionsApprovedService(String message) {
        String normalized = normalize(message);
        return approvedServiceAliases().stream().anyMatch(alias -> containsTerm(normalized, alias));
    }

    private boolean mentionsUnsupportedCondition(String message) {
        return UNSUPPORTED_CONDITION_PATTERN.matcher(normalize(message)).find();
    }

    private static boolean meaningfulFactValue(String value) {
        return value != null && normalize(value).length() >= 3;
    }

    private static boolean containsTerm(String normalizedText, String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return false;
        }
        Pattern term = Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(normalizedValue)
                + "(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return term.matcher(normalizedText).find();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record PriceMention(BigDecimal amount, int start, int end) {
    }

    private record ServiceMention(CommercialPolicyService.ServiceFixture service, int start, int end) {
    }

    public record ValidationResult(boolean accepted, String reason, AgentOutput output) {
        public ValidationResult {
            Objects.requireNonNull(output, "output");
            if (accepted && reason != null) {
                throw new IllegalArgumentException("accepted result cannot have a rejection reason");
            }
            if (!accepted && (reason == null || reason.isBlank())) {
                throw new IllegalArgumentException("rejected result requires a reason");
            }
        }

        public static ValidationResult accepted(AgentOutput output) {
            return new ValidationResult(true, null, output);
        }

        public static ValidationResult rejected(String reason, AgentOutput fallback) {
            return new ValidationResult(false, reason, fallback);
        }
    }
}
