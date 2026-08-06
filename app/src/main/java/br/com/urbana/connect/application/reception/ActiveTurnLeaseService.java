package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/** Binds plugin calls to the canonical turn and revokes authorization in finally. */
public class ActiveTurnLeaseService {
    private final ActiveTurnLeaseGateway gateway;
    private final Clock clock;
    private final Duration ttl;

    public ActiveTurnLeaseService(ActiveTurnLeaseGateway gateway) {
        this(gateway, Clock.systemUTC(), Duration.ofSeconds(60));
    }

    public ActiveTurnLeaseService(ActiveTurnLeaseGateway gateway, Clock clock, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("lease ttl must be positive");
        }
        this.gateway = gateway;
        this.clock = clock;
        this.ttl = ttl;
    }

    public ActiveTurnLease acquire(String hermesSessionId, String turnId, String contactId, String sourceMessageId) {
        Instant acquired = clock.instant();
        ActiveTurnLease requested = new ActiveTurnLease(hermesSessionId, turnId, contactId, sourceMessageId,
                br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus.RUNNING,
                acquired, acquired.plus(ttl), null, 0);
        return gateway.acquire(requested).orElseThrow(() -> new LeaseUnavailableException(
                "another running turn already owns session " + hermesSessionId));
    }

    public ActiveTurnLease requireActive(String sessionId) {
        return requireActive(sessionId, null, null, null);
    }

    public ActiveTurnLease requireActive(String sessionId, String expectedTurnId, String expectedContactId,
                                         String expectedSourceMessageId) {
        Instant now = clock.instant();
        ActiveTurnLease lease = gateway.findRunning(sessionId, now)
                .orElseThrow(() -> new LeaseRejectedException("active lease is absent, expired or revoked"));
        if (expectedTurnId != null && !expectedTurnId.equals(lease.turnId())) {
            throw new LeaseRejectedException("lease turn binding mismatch");
        }
        if (expectedContactId != null && !expectedContactId.equals(lease.contactId())) {
            throw new LeaseRejectedException("lease contact binding mismatch");
        }
        if (expectedSourceMessageId != null && !expectedSourceMessageId.equals(lease.sourceMessageId())) {
            throw new LeaseRejectedException("lease source message binding mismatch");
        }
        return lease;
    }

    public ActiveTurnLease revoke(ActiveTurnLease lease) {
        if (lease == null) {
            return null;
        }
        if (lease.status() != br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus.RUNNING) {
            return lease;
        }
        return gateway.revoke(lease.hermesSessionId(), lease.turnId(), clock.instant());
    }

    public <T> T withLease(String sessionId, String turnId, String contactId, String sourceMessageId,
                           Supplier<T> action) {
        ActiveTurnLease lease = acquire(sessionId, turnId, contactId, sourceMessageId);
        Throwable actionFailure = null;
        try {
            return action.get();
        } catch (RuntimeException | Error failure) {
            actionFailure = failure;
            throw failure;
        } finally {
            try {
                revoke(lease);
            } catch (RuntimeException cleanupFailure) {
                // A lease can expire or be revoked concurrently with a slow
                // upstream call. Never replace a completed turn (or the
                // original action failure) with cleanup noise.
                if (actionFailure != null) {
                    actionFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    public void withLease(String sessionId, String turnId, String contactId, String sourceMessageId,
                          Runnable action) {
        withLease(sessionId, turnId, contactId, sourceMessageId, () -> {
            action.run();
            return null;
        });
    }

    public static class LeaseRejectedException extends IllegalArgumentException {
        public LeaseRejectedException(String message) { super(message); }
    }

    public static class LeaseUnavailableException extends LeaseRejectedException {
        public LeaseUnavailableException(String message) { super(message); }
    }
}
