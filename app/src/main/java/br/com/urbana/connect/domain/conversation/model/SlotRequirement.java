package br.com.urbana.connect.domain.conversation.model;

public record SlotRequirement(
        ConversationSlotName slot,
        ConversationSlotLevel minimumLevel) {
}
