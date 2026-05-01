package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationalAiAction;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class ConversationResponseValidator {

    private static final int MAX_REPLY_LENGTH = 320;
    private static final Pattern PRICE_PATTERN = Pattern.compile("R\\$\\s*\\d+[\\d.,]*");
    private static final Pattern META_SPEECH_PATTERN = Pattern.compile(
        "(agora vou|agora eu vou|o próximo passo|vou coletar|vamos coletar|neste passo)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ
    );

    public ResponseValidationResult validate(
            StepContract stepContract,
            ConversationalAiReply reply,
            List<ServiceCatalogItem> availableServices) {
        ResponseValidationResult structuralValidation = validateStructure(stepContract, reply);
        if (!structuralValidation.valid()) {
            return structuralValidation;
        }

        ResponseValidationResult replyValidation = validateReplyText(reply.replyText(), availableServices, reply.action());
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
            List<ServiceCatalogItem> availableServices,
            ConversationalAiAction action) {
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
        if (mentionsUnknownService(replyText, action, availableServices)) {
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
            ConversationalAiAction action,
            List<ServiceCatalogItem> availableServices) {
        if (action != ConversationalAiAction.PROPOSE_SERVICE) {
            return false;
        }

        String normalizedReply = replyText.toLowerCase(Locale.ROOT);
        return availableServices.stream().noneMatch(service ->
            normalizedReply.contains(service.name().toLowerCase(Locale.ROOT))
                || normalizedReply.contains(service.type().name().toLowerCase(Locale.ROOT).replace('_', ' '))
        );
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
}
