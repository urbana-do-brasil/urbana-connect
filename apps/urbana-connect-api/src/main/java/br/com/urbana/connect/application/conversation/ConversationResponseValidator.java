package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ConversationResponseValidator {

    private static final int MAX_REPLY_LENGTH = 320;
    private static final Pattern PRICE_PATTERN = Pattern.compile("R\\$\\s*\\d+[\\d.,]*");
    private static final Pattern META_SPEECH_PATTERN = Pattern.compile(
        "(agora vou|agora eu vou|o próximo passo|vou coletar|vamos coletar|neste passo)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ
    );
    private static final Pattern SERVICE_REFERENCE_CUE_PATTERN = Pattern.compile(
        "\\b(serviço|opção|pacote|projeto)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ
    );
    private static final Set<String> IGNORED_NAMED_PHRASES = Set.of("a urba", "urbana do brasil");

    public ResponseValidationResult validate(
            StepContract stepContract,
            ConversationalAiReply reply,
            List<ServiceCatalogItem> availableServices) {
        ResponseValidationResult structuralValidation = validateStructure(stepContract, reply);
        if (!structuralValidation.valid()) {
            return structuralValidation;
        }

        ResponseValidationResult replyValidation = validateReplyText(reply.replyText(), availableServices);
        if (!replyValidation.valid()) {
            return replyValidation;
        }

        return validateSlotUpdates(reply.slotUpdates());
    }

    private ResponseValidationResult validateStructure(StepContract stepContract, ConversationalAiReply reply) {
        if (reply == null || !reply.isStructurallyValid()) {
            return ResponseValidationResult.rejected("invalid_structure");
        }
        if (!stepContract.allowedActions().contains(reply.action()) || stepContract.forbiddenActions().contains(reply.action())) {
            return ResponseValidationResult.rejected("action_not_allowed");
        }
        if (reply.replyText() == null || reply.replyText().isBlank()) {
            return ResponseValidationResult.rejected("empty_reply");
        }
        return ResponseValidationResult.accepted();
    }

    private ResponseValidationResult validateReplyText(
            String replyText,
            List<ServiceCatalogItem> availableServices) {
        if (replyText.length() > MAX_REPLY_LENGTH) {
            return ResponseValidationResult.rejected("reply_too_long");
        }
        if (countSentences(replyText) > 3) {
            return ResponseValidationResult.rejected("too_many_sentences");
        }
        if (countQuestions(replyText) > 1) {
            return ResponseValidationResult.rejected("too_many_questions");
        }
        if (META_SPEECH_PATTERN.matcher(replyText).find()) {
            return ResponseValidationResult.rejected("meta_speech");
        }
        if (mentionsUnknownService(replyText, availableServices)) {
            return ResponseValidationResult.rejected("unknown_service_mention");
        }
        if (mentionsDivergentPrice(replyText, availableServices)) {
            return ResponseValidationResult.rejected("divergent_price");
        }
        return ResponseValidationResult.accepted();
    }

    private ResponseValidationResult validateSlotUpdates(List<ConversationSlotUpdate> slotUpdates) {
        boolean invalidSlotUpdate = slotUpdates.stream().anyMatch(slotUpdate ->
            slotUpdate == null || slotUpdate.slot() == null || slotUpdate.value() == null || slotUpdate.value().isBlank()
        );
        return invalidSlotUpdate
            ? ResponseValidationResult.rejected("invalid_slot_update")
            : ResponseValidationResult.accepted();
    }

    private boolean mentionsUnknownService(
            String replyText,
            List<ServiceCatalogItem> availableServices) {
        String normalizedReply = normalize(replyText);
        Set<String> availableAliases = availableServices.stream()
            .flatMap(service -> Stream.of(
                normalize(service.name()),
                normalize(service.type().name().replace('_', ' ')),
                normalize(legacyAliasFor(service.type()))
            ))
            .filter(alias -> !alias.isBlank())
            .collect(Collectors.toSet());
        if (availableAliases.stream().anyMatch(normalizedReply::contains)) {
            return false;
        }

        boolean mentionsUnavailableCatalogAlias = Stream.of(ServiceType.values())
            .map(serviceType -> normalize(serviceType.name().replace('_', ' ')))
            .filter(alias -> !availableAliases.contains(alias))
            .anyMatch(normalizedReply::contains);
        if (mentionsUnavailableCatalogAlias) {
            return true;
        }

        if (!SERVICE_REFERENCE_CUE_PATTERN.matcher(replyText).find()) {
            return false;
        }

        for (String candidate : extractCapitalizedPhrases(replyText)) {
            if (!IGNORED_NAMED_PHRASES.contains(candidate) && !availableAliases.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean mentionsDivergentPrice(String replyText, List<ServiceCatalogItem> availableServices) {
        var matcher = PRICE_PATTERN.matcher(replyText);
        if (!matcher.find()) {
            return false;
        }
        String normalized = matcher.group().replace("R$", "").replace(" ", "").replace(".", "").replace(",", ".");
        try {
            BigDecimal quoted = new BigDecimal(normalized);
            return availableServices.stream()
                .map(ServiceCatalogItem::price)
                .filter(Objects::nonNull)
                .noneMatch(price -> price.compareTo(quoted) == 0);
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    private int countSentences(String replyText) {
        return (int) replyText.chars()
            .filter(character -> character == '.' || character == '!' || character == '?')
            .count();
    }

    private int countQuestions(String replyText) {
        return (int) replyText.chars().filter(character -> character == '?').count();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String legacyAliasFor(ServiceType type) {
        return ServiceType.canonicalize(type) == ServiceType.DECOR_INTERIORES ? "Decor" : "";
    }

    private List<String> extractCapitalizedPhrases(String replyText) {
        List<String> phrases = new ArrayList<>();
        StringBuilder currentPhrase = new StringBuilder();
        int capitalizedWords = 0;

        for (String token : replyText.replaceAll("[^\\p{L}\\s]", " ").split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (startsWithUppercase(token)) {
                if (!currentPhrase.isEmpty()) {
                    currentPhrase.append(' ');
                }
                currentPhrase.append(token);
                capitalizedWords++;
                continue;
            }
            addPhraseIfRelevant(phrases, currentPhrase, capitalizedWords);
            currentPhrase.setLength(0);
            capitalizedWords = 0;
        }

        addPhraseIfRelevant(phrases, currentPhrase, capitalizedWords);
        return phrases;
    }

    private void addPhraseIfRelevant(List<String> phrases, StringBuilder currentPhrase, int capitalizedWords) {
        if (capitalizedWords >= 2 && !currentPhrase.isEmpty()) {
            phrases.add(normalize(currentPhrase.toString()));
        }
    }

    private boolean startsWithUppercase(String token) {
        return !token.isBlank() && Character.isUpperCase(token.codePointAt(0));
    }
}
