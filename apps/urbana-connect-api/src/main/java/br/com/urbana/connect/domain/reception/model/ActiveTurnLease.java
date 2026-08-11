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
        long version,
        String claimToken) {
    public ActiveTurnLease(String hermesSessionId, String turnId, String contactId, String sourceMessageId,
                           ActiveTurnLeaseStatus status, Instant acquiredAt, Instant expiresAt,
                           Instant revokedAt, long version) {
        this(hermesSessionId, turnId, contactId, sourceMessageId, status, acquiredAt, expiresAt,
                revokedAt, version, turnId + ":" + version);
    }

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
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        claimToken = claimToken == null || claimToken.isBlank()
                ? turnId + ":" + version : claimToken;
    }

    public boolean isActiveAt(Instant now) {
        return status == ActiveTurnLeaseStatus.RUNNING && now.isBefore(expiresAt);
    }

    public boolean blocksNewTurnAt(Instant now) {
        return status == ActiveTurnLeaseStatus.RECONCILING
                || isActiveAt(now);
    }

    public ActiveTurnLease revoke(Instant now) {
        if (status != ActiveTurnLeaseStatus.RUNNING && status != ActiveTurnLeaseStatus.RECONCILING) {
            return this;
        }
        return new ActiveTurnLease(hermesSessionId, turnId, contactId, sourceMessageId,
                ActiveTurnLeaseStatus.REVOKED, acquiredAt, expiresAt, now, version + 1, claimToken);
    }

    public ActiveTurnLease expire(Instant now) {
        if (status != ActiveTurnLeaseStatus.RUNNING || now.isBefore(expiresAt)) {
            return this;
        }
        return new ActiveTurnLease(hermesSessionId, turnId, contactId, sourceMessageId,
                ActiveTurnLeaseStatus.EXPIRED, acquiredAt, expiresAt, null, version + 1, claimToken);
    }

    public ActiveTurnLease reconcile(Instant now) {
        if (status != ActiveTurnLeaseStatus.RUNNING) {
            return this;
        }
        return new ActiveTurnLease(hermesSessionId, turnId, contactId, sourceMessageId,
                ActiveTurnLeaseStatus.RECONCILING, acquiredAt, expiresAt, null, version + 1, claimToken);
    }

    public ActiveTurnLease heartbeat(Instant now, java.time.Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("lease ttl must be positive");
        }
        if (status != ActiveTurnLeaseStatus.RUNNING && status != ActiveTurnLeaseStatus.RECONCILING) {
            return this;
        }
        return new ActiveTurnLease(hermesSessionId, turnId, contactId, sourceMessageId,
                status, acquiredAt, now.plus(ttl), revokedAt, version + 1, claimToken);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
