package br.com.urbana.connect.domain.conversation.model;

public record ConversationSlotValue(
        String value,
        ConversationSlotLevel level,
        ConversationSlotSource source,
        Double confidence) {

    public boolean satisfies(ConversationSlotLevel requiredLevel) {
        return level != null && level.satisfies(requiredLevel);
    }

    public ConversationSlotValue normalized() {
        return new ConversationSlotValue(
            value,
            level == null ? ConversationSlotLevel.UNKNOWN : level,
            source == null ? ConversationSlotSource.INFERRED : source,
            confidence
        );
    }
}
