package br.com.urbana.connect.domain.conversation.model;

import java.util.List;

public record ConversationalAiReply(
        String replyText,
        ConversationalAiAction action,
        List<ConversationSlotUpdate> slotUpdates,
        Double confidence,
        boolean shouldAdvance,
        ConversationStep suggestedNextStep,
        boolean shouldOfferStructuredOptions,
        String fallbackReason) {

    public ConversationalAiReply {
        slotUpdates = slotUpdates == null ? List.of() : List.copyOf(slotUpdates);
    }

    public static ConversationalAiReply fallback(String fallbackReason) {
        return new ConversationalAiReply(
            null,
            ConversationalAiAction.REPEAT_WITH_REFRAME,
            List.of(),
            0.0,
            false,
            null,
            false,
            fallbackReason
        );
    }

    public boolean isStructurallyValid() {
        return action != null
            && confidence != null
            && confidence >= 0.0
            && confidence <= 1.0
            && slotUpdates != null;
    }
}
