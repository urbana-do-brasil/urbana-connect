package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.AssembledContext;
import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ConversationResponseValidator {

    private static final int MAX_REPLY_LENGTH = 320;
    private static final Pattern PRICE_PATTERN = Pattern.compile("R\\$\\s*\\d+[\\d.,]*");
    private static final Pattern META_SPEECH_PATTERN = Pattern.compile(
        "(agora vou|agora eu vou|o próximo passo|vou coletar|vamos coletar|neste passo)",
        Pattern.CASE_INSENSITIVE
    );

    public ResponseValidationResult validate(
            Conversation conversation,
            AssembledContext assembledContext,
            StepContract stepContract,
            ConversationalAiReply reply,
            List<ServiceCatalogItem> availableServices) {
        if (reply == null || !reply.isStructurallyValid()) {
            return ResponseValidationResult.rejected("invalid_structure");
        }
        if (!stepContract.allowedActions().contains(reply.action()) || stepContract.forbiddenActions().contains(reply.action())) {
            return ResponseValidationResult.rejected("action_not_allowed");
        }
        if (reply.replyText() == null || reply.replyText().isBlank()) {
            return ResponseValidationResult.rejected("empty_reply");
        }
        if (reply.replyText().length() > MAX_REPLY_LENGTH) {
            return ResponseValidationResult.rejected("reply_too_long");
        }
        if (countSentences(reply.replyText()) > 3) {
            return ResponseValidationResult.rejected("too_many_sentences");
        }
        if (countQuestions(reply.replyText()) > 1) {
            return ResponseValidationResult.rejected("too_many_questions");
        }
        if (META_SPEECH_PATTERN.matcher(reply.replyText()).find()) {
            return ResponseValidationResult.rejected("meta_speech");
        }
        if (mentionsUnknownService(reply, availableServices)) {
            return ResponseValidationResult.rejected("unknown_service_mention");
        }
        if (mentionsDivergentPrice(reply.replyText(), availableServices)) {
            return ResponseValidationResult.rejected("divergent_price");
        }
        for (ConversationSlotUpdate slotUpdate : reply.slotUpdates()) {
            if (slotUpdate == null || slotUpdate.slot() == null || slotUpdate.value() == null || slotUpdate.value().isBlank()) {
                return ResponseValidationResult.rejected("invalid_slot_update");
            }
        }
        return ResponseValidationResult.accepted();
    }

    private boolean mentionsUnknownService(ConversationalAiReply reply, List<ServiceCatalogItem> availableServices) {
        if (reply.replyText() == null || reply.replyText().isBlank()) {
            return false;
        }

        String normalizedReply = reply.replyText().toLowerCase(Locale.ROOT);
        boolean mentionsDecor = normalizedReply.contains("decor");
        if (!mentionsDecor) {
            return false;
        }

        boolean validMention = availableServices.stream().anyMatch(service ->
            normalizedReply.contains(service.name().toLowerCase(Locale.ROOT))
                || normalizedReply.contains(service.type().name().toLowerCase(Locale.ROOT).replace('_', ' '))
        );
        return !validMention && reply.action() == br.com.urbana.connect.domain.conversation.model.ConversationalAiAction.PROPOSE_SERVICE;
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
                .filter(price -> price != null)
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
