package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;

public record AgentSessionLink(
        String contactId,
        String hermesSessionId,
        SessionLinkStatus status,
        Instant createdAt,
        Instant lastUsedAt,
        String replacedBySessionId,
        long version) {
    public AgentSessionLink {
        require(contactId, "contactId");
        require(hermesSessionId, "hermesSessionId");
        if (status == null || createdAt == null || lastUsedAt == null) {
            throw new IllegalArgumentException("status and timestamps are required");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
    }

    public static AgentSessionLink active(String contactId, String sessionId, Instant now) {
        return new AgentSessionLink(contactId, sessionId, SessionLinkStatus.ACTIVE, now, now, null, 0);
    }

    public AgentSessionLink touch(Instant now) {
        return new AgentSessionLink(contactId, hermesSessionId, status, createdAt, now,
                replacedBySessionId, version + 1);
    }

    public AgentSessionLink markLost(Instant now) {
        return new AgentSessionLink(contactId, hermesSessionId, SessionLinkStatus.LOST,
                createdAt, now, replacedBySessionId, version + 1);
    }

    public AgentSessionLink replaceWith(String replacementSessionId, Instant now) {
        require(replacementSessionId, "replacementSessionId");
        return new AgentSessionLink(contactId, hermesSessionId, SessionLinkStatus.REPLACED,
                createdAt, now, replacementSessionId, version + 1);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
