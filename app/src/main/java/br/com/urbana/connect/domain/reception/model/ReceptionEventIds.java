package br.com.urbana.connect.domain.reception.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Namespaced transcript event identities. External inbound IDs cannot enter the outbound namespace. */
public final class ReceptionEventIds {
    public static final String OUTBOUND_PREFIX = "urn:urbana:outbound:";

    private ReceptionEventIds() {
    }

    public static String outbound(String sourceEventId, String correlationId) {
        if (sourceEventId == null || sourceEventId.isBlank() || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("sourceEventId and correlationId are required");
        }
        String material = sourceEventId + "\u0000" + correlationId;
        return OUTBOUND_PREFIX + UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isReserved(String eventId) {
        return eventId != null && eventId.startsWith(OUTBOUND_PREFIX);
    }
}
