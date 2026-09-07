package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable side-effect intent.  Delivery is intentionally outside the
 * transaction that records terms and conversation state.
 */
public record DeliveryOutbox(
        String eventKey,
        DeliveryOutboxKind kind,
        String destinationRef,
        String payload,
        DeliveryOutboxStatus status,
        int attempts,
        Instant nextAttemptAt,
        String providerMessageId,
        String correlationId,
        Instant createdAt,
        Instant sentAt) {

    public DeliveryOutbox {
        require(eventKey, "eventKey");
        kind = Objects.requireNonNull(kind, "kind");
        require(destinationRef, "destinationRef");
        require(payload, "payload");
        status = Objects.requireNonNull(status, "status");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must be non-negative");
        }
        nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        require(correlationId, "correlationId");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (sentAt != null && sentAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("sentAt cannot precede createdAt");
        }
    }

    public static DeliveryOutbox pending(String eventKey, DeliveryOutboxKind kind,
                                         String destinationRef, String payload,
                                         String correlationId, Instant now) {
        return new DeliveryOutbox(eventKey, kind, destinationRef, payload,
                DeliveryOutboxStatus.PENDING, 0, now, null, correlationId, now, null);
    }

    public DeliveryOutbox sending(Instant now) {
        return copy(DeliveryOutboxStatus.SENDING, attempts + 1, now, providerMessageId, null);
    }

    public DeliveryOutbox sent(String providerMessageId, Instant now) {
        return copy(DeliveryOutboxStatus.SENT, attempts, now, providerMessageId, now);
    }

    public DeliveryOutbox retryable(Instant nextAttemptAt) {
        return copy(DeliveryOutboxStatus.RETRYABLE, attempts, nextAttemptAt, providerMessageId, sentAt);
    }

    public DeliveryOutbox ambiguous(Instant observedAt) {
        return copy(DeliveryOutboxStatus.AMBIGUOUS, attempts, observedAt, providerMessageId, sentAt);
    }

    public DeliveryOutbox deadLetter(Instant observedAt) {
        return copy(DeliveryOutboxStatus.DEAD_LETTER, attempts, observedAt, providerMessageId, sentAt);
    }

    private DeliveryOutbox copy(DeliveryOutboxStatus nextStatus, int nextAttempts,
                                Instant nextAttemptAt, String nextProviderMessageId,
                                Instant nextSentAt) {
        return new DeliveryOutbox(eventKey, kind, destinationRef, payload, nextStatus,
                nextAttempts, Objects.requireNonNull(nextAttemptAt, "nextAttemptAt"),
                nextProviderMessageId, correlationId, createdAt, nextSentAt);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
