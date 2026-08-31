package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
        return acquire(hermesSessionId, turnId, contactId, sourceMessageId, List.of(sourceMessageId));
    }

    public ActiveTurnLease acquire(String hermesSessionId, String turnId, String contactId, String sourceMessageId,
                                   List<String> sourceMessageIds) {
        Instant acquired = clock.instant();
        ActiveTurnLease requested = new ActiveTurnLease(hermesSessionId, turnId, contactId, sourceMessageId,
                br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus.RUNNING,
                acquired, acquired.plus(ttl), null, 0, UUID.randomUUID().toString(), sourceMessageIds);
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

    public ActiveTurnLease requireBlocking(String sessionId, String expectedTurnId, String expectedContactId) {
        ActiveTurnLease lease = gateway.findBlocking(sessionId, clock.instant())
                .orElseThrow(() -> new LeaseRejectedException("blocking lease is absent"));
        if (expectedTurnId != null && !expectedTurnId.equals(lease.turnId())) {
            throw new LeaseRejectedException("lease turn binding mismatch");
        }
        if (expectedContactId != null && !expectedContactId.equals(lease.contactId())) {
            throw new LeaseRejectedException("lease contact binding mismatch");
        }
        return lease;
    }

    public ActiveTurnLease revoke(ActiveTurnLease lease) {
        if (lease == null) {
            return null;
        }
        if (lease.status() != br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus.RUNNING
                && lease.status() != br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus.RECONCILING) {
            return lease;
        }
        return gateway.revoke(lease.hermesSessionId(), lease.turnId(), lease.claimToken(), clock.instant());
    }

    public ActiveTurnLease holdForReconciliation(ActiveTurnLease lease) {
        if (lease == null) return null;
        return gateway.markReconciling(lease.hermesSessionId(), lease.turnId(), lease.claimToken(), clock.instant());
    }

    public ActiveTurnLease heartbeat(ActiveTurnLease lease) {
        if (lease == null) return null;
        return gateway.heartbeat(lease.hermesSessionId(), lease.turnId(), lease.claimToken(),
                clock.instant(), ttl);
    }

    /** Releases a gate only after the reconciler has persisted a canonical result. */
    public void releaseForReconciliation(String sessionId, String turnId) {
        try {
            gateway.revoke(sessionId, turnId, clock.instant());
        } catch (RuntimeException ignored) {
            // The lease may have expired or already been released by a racing
            // reconciler. The durable turn and transcript remain authoritative.
        }
    }

    public <T> T withLease(String sessionId, String turnId, String contactId, String sourceMessageId,
                           Supplier<T> action) {
        return withLease(sessionId, turnId, contactId, sourceMessageId, List.of(sourceMessageId), action);
    }

    public <T> T withLease(String sessionId, String turnId, String contactId, String sourceMessageId,
                           List<String> sourceMessageIds, Supplier<T> action) {
        ActiveTurnLease lease = acquire(sessionId, turnId, contactId, sourceMessageId, sourceMessageIds);
        AtomicReference<ActiveTurnLease> currentLease = new AtomicReference<>(lease);
        ScheduledExecutorService heartbeatExecutor = newHeartbeatExecutor();
        ScheduledFuture<?> heartbeatTask = scheduleHeartbeat(heartbeatExecutor, currentLease);
        Throwable actionFailure = null;
        boolean retainForReconciliation = false;
        try {
            return action.get();
        } catch (RuntimeException | Error failure) {
            actionFailure = failure;
            if (isAmbiguousHermesFailure(failure)) {
                // Do not revoke an authorization while the remote dispatch may
                // still be running. Keeping the gate prevents a second turn on
                // this session from racing the unknown first result.
                retainForReconciliation = true;
                try {
                    holdForReconciliation(lease);
                } catch (RuntimeException holdFailure) {
                    failure.addSuppressed(holdFailure);
                }
            }
            throw failure;
        } finally {
            stopHeartbeat(heartbeatExecutor, heartbeatTask);
            if (!retainForReconciliation) {
                try {
                    revoke(currentLease.get());
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
    }

    private ScheduledExecutorService newHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "urbana-active-turn-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    private ScheduledFuture<?> scheduleHeartbeat(ScheduledExecutorService executor,
                                                  AtomicReference<ActiveTurnLease> currentLease) {
        long intervalNanos = heartbeatInterval().toNanos();
        return executor.scheduleAtFixedRate(() -> {
            try {
                ActiveTurnLease refreshed = heartbeat(currentLease.get());
                if (refreshed != null) {
                    currentLease.set(refreshed);
                }
            } catch (RuntimeException ignored) {
                // A failed renewal must not replace the remote action result or
                // prevent the next renewal attempt from running.
            }
        }, intervalNanos, intervalNanos, TimeUnit.NANOSECONDS);
    }

    private Duration heartbeatInterval() {
        Duration interval = ttl.dividedBy(2);
        return interval.isZero() || interval.isNegative() ? Duration.ofNanos(1) : interval;
    }

    private static void stopHeartbeat(ScheduledExecutorService executor, ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static boolean isAmbiguousHermesFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof HttpHermesSessionsGateway.HermesSessionsException exception
                    && exception.phase() == HttpHermesSessionsGateway.HermesFailurePhase.POST_DISPATCH_AMBIGUOUS) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public void withLease(String sessionId, String turnId, String contactId, String sourceMessageId,
                          Runnable action) {
        withLease(sessionId, turnId, contactId, sourceMessageId, () -> {
            action.run();
            return null;
        });
    }

    public void withLease(String sessionId, String turnId, String contactId, String sourceMessageId,
                          List<String> sourceMessageIds, Runnable action) {
        withLease(sessionId, turnId, contactId, sourceMessageId, sourceMessageIds, () -> {
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
