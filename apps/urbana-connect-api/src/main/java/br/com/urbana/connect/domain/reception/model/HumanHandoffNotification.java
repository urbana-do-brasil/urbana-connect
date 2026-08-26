package br.com.urbana.connect.domain.reception.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal handoff notification payload; never serialized to the customer. */
public record HumanHandoffNotification(
        String notificationId,
        String idempotencyKey,
        String conversationId,
        String turnId,
        String reason,
        String serviceType,
        String commercialStage,
        String paymentStatus,
        List<String> presentIcpFields,
        List<String> missingIcpFields,
        Instant occurredAt) {

    public HumanHandoffNotification {
        notificationId = required(notificationId, "notificationId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        conversationId = required(conversationId, "conversationId");
        turnId = required(turnId, "turnId");
        reason = required(reason, "reason");
        serviceType = optional(serviceType);
        commercialStage = optional(commercialStage);
        paymentStatus = optional(paymentStatus);
        presentIcpFields = copy(presentIcpFields);
        missingIcpFields = copy(missingIcpFields);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static HumanHandoffNotification create(String idempotencyKey, String conversationId,
                                                  String turnId, String reason, String serviceType,
                                                  String commercialStage, String paymentStatus,
                                                  List<String> presentIcpFields,
                                                  List<String> missingIcpFields, Instant occurredAt) {
        String notificationId = "urn:urbana:reception:handoff:"
                + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        return new HumanHandoffNotification(notificationId, idempotencyKey, conversationId, turnId,
                reason, serviceType, commercialStage, paymentStatus, presentIcpFields,
                missingIcpFields, occurredAt);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank())
                .distinct().toList();
    }

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
