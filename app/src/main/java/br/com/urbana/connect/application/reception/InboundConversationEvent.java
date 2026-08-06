package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionEventIds;

import java.time.Instant;

/**
 * Canonical inbound contract shared by the local simulator and future channel
 * adapters.  Channel-specific identifiers stay at the edge; the application
 * only receives the opaque contact id and normalized content.
 */
public record InboundConversationEvent(
        String eventId,
        String contactId,
        ReceptionMessageType type,
        String text,
        String transcript,
        String mediaFixture,
        String interactiveReplyId,
        Instant occurredAt,
        String providerMessageId) {

    public InboundConversationEvent {
        require(eventId, "eventId");
        if (ReceptionEventIds.isReserved(eventId)) {
            throw new IllegalArgumentException("eventId belongs to a reserved outbound namespace");
        }
        require(contactId, "contactId");
        if (type == null || occurredAt == null) {
            throw new IllegalArgumentException("type and occurredAt are required");
        }
        if (isBlank(text) && isBlank(transcript) && isBlank(mediaFixture) && isBlank(interactiveReplyId)) {
            throw new IllegalArgumentException("event must contain text, transcript, mediaFixture or interactiveReplyId");
        }
        text = normalize(text);
        transcript = normalize(transcript);
        mediaFixture = normalize(mediaFixture);
        interactiveReplyId = normalize(interactiveReplyId);
        providerMessageId = normalize(providerMessageId);
    }

    public InboundConversationEvent(String eventId, String contactId, ReceptionMessageType type,
                                   String text, Instant occurredAt) {
        this(eventId, contactId, type, text, null, null, null, occurredAt, null);
    }

    public String conversationalText() {
        if (type == ReceptionMessageType.AUDIO && !isBlank(transcript)) {
            return transcript;
        }
        if (!isBlank(text)) {
            return text;
        }
        return type == ReceptionMessageType.INTERACTIVE && !isBlank(interactiveReplyId)
                ? interactiveReplyId : "";
    }

    public boolean isPaymentProof() {
        if (type == ReceptionMessageType.PAYMENT_PROOF) {
            return true;
        }
        if ((type != ReceptionMessageType.IMAGE && type != ReceptionMessageType.DOCUMENT)
                || isBlank(mediaFixture)) {
            return false;
        }
        String normalized = mediaFixture.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("comprovante")
                || normalized.contains("payment-proof")
                || normalized.contains("payment_proof");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void require(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
