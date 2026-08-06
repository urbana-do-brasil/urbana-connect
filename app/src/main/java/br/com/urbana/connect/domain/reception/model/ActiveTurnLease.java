package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;

public record ActiveTurnLease(
        String hermesSessionId,
        String turnId,
        String contactId,
        String sourceMessageId,
        ActiveTurnLeaseStatus status,
        Instant acquiredAt,
        Instant expiresAt,
        Instant revokedAt,
        long version) {
    public ActiveTurnLease {
        require(hermesSessionId, "hermesSessionId");
        require(turnId, "turnId");
        require(contactId, "contactId");
        require(sourceMessageId, "sourceMessageId");
        if (status == null || acquiredAt == null || expiresAt == null) {
            throw new IllegalArgumentException("lease status and timestamps are required");
        }
        if (!expiresAt.isAfter(acquiredAt)) {
            throw new IllegalArgumentException("expiresAt must be after acquiredAt");
        }
    }

    public boolean isActiveAt(Instant now) {
        return status == ActiveTurnLeaseStatus.RUNNING && now.isBefore(expiresAt);
    }

    public ActiveTurnLease revoke(Instant now) {
        if (status != ActiveTurnLeaseStatus.RUNNING) {
            return this;
        }
        return new ActiveTurnLease(hermesSessionId, turnId, contactId, sourceMessageId,
                ActiveTurnLeaseStatus.REVOKED, acquiredAt, expiresAt, now, version + 1);
    }

    public ActiveTurnLease expire(Instant now) {
        if (status != ActiveTurnLeaseStatus.RUNNING || now.isBefore(expiresAt)) {
            return this;
        }
        return new ActiveTurnLease(hermesSessionId, turnId, contactId, sourceMessageId,
                ActiveTurnLeaseStatus.EXPIRED, acquiredAt, expiresAt, null, version + 1);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
