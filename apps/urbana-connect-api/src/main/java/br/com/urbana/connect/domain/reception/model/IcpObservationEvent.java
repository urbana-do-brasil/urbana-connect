package br.com.urbana.connect.domain.reception.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal-only observability for a commercial continuation that intentionally
 * proceeded without every conversational qualification field.
 *
 * <p>The shape deliberately has no customer-provided value, transcript text,
 * or fact payload. It is not a transcript message and must never be returned
 * by a domain tool.</p>
 */
public record IcpObservationEvent(
        String eventId,
        String eventType,
        String conversationId,
        String turnId,
        String serviceType,
        List<String> missingFields,
        String detectionPoint,
        String idempotencyKey,
        Instant occurredAt) {

    public static final String TYPE = "ICP_SKIPPED_BEFORE_TERMS";
    public static final String DETECTION_POINT = "PREPARE_TERMS";

    public IcpObservationEvent {
        eventId = required(eventId, "eventId");
        eventType = required(eventType, "eventType");
        if (!TYPE.equals(eventType)) {
            throw new IllegalArgumentException("unsupported ICP observation event type");
        }
        conversationId = required(conversationId, "conversationId");
        turnId = required(turnId, "turnId");
        serviceType = required(serviceType, "serviceType");
        missingFields = missingFields == null ? List.of() : missingFields.stream()
                .map(field -> required(field, "missingField"))
                .distinct()
                .toList();
        if (missingFields.isEmpty()) {
            throw new IllegalArgumentException("missingFields must not be empty");
        }
        detectionPoint = required(detectionPoint, "detectionPoint");
        if (!DETECTION_POINT.equals(detectionPoint)) {
            throw new IllegalArgumentException("unsupported ICP observation detection point");
        }
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static IcpObservationEvent beforeTerms(String conversationId, String turnId,
                                                  String serviceType, List<String> missingFields,
                                                  String idempotencyKey, Instant occurredAt) {
        String eventId = "urn:urbana:reception:icp:"
                + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        return new IcpObservationEvent(eventId, TYPE, conversationId, turnId, serviceType,
                missingFields, DETECTION_POINT, idempotencyKey, occurredAt);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
