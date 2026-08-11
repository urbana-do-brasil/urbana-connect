package br.com.urbana.connect.domain.conversation.model;

public record ConversationSlotUpdate(
        ConversationSlotName slot,
        String value,
        ConversationSlotLevel level,
        Double confidence,
        ConversationSlotSource source) {

    public ConversationSlotValue toSlotValue() {
        return new ConversationSlotValue(value, level, source, confidence).normalized();
    }
}
