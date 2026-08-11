package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;

public record ReceptionMessage(
        String id,
        String eventId,
        String correlationId,
        String conversationId,
        String contactId,
        ReceptionMessageDirection direction,
        ReceptionMessageSender senderType,
        ReceptionMessageType type,
        String text,
        String mediaRef,
        String providerMessageId,
        Instant createdAt) {
    public ReceptionMessage {
        require(id, "id");
        require(eventId, "eventId");
        if (direction == ReceptionMessageDirection.INBOUND && ReceptionEventIds.isReserved(eventId)) {
            throw new IllegalArgumentException("inbound eventId belongs to a reserved outbound namespace");
        }
        require(correlationId, "correlationId");
        require(conversationId, "conversationId");
        require(contactId, "contactId");
        if (direction == null || senderType == null || type == null || createdAt == null) {
            throw new IllegalArgumentException("message direction, sender, type and time are required");
        }
        if (text == null && mediaRef == null) {
            throw new IllegalArgumentException("message must include text or mediaRef");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
