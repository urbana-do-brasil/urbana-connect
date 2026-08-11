package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveTurnLeaseServiceTest {
    private final Instant now = Instant.parse("2026-08-04T12:00:00Z");

    @Test
    void rejectsSecondRunningLeaseAndRevokesInFinally() {
        FakeLeaseGateway gateway = new FakeLeaseGateway();
        ActiveTurnLeaseService service = new ActiveTurnLeaseService(gateway,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(30));
        ActiveTurnLease first = service.acquire("session-1", "turn-1", "contact-1", "message-1");

        assertThatThrownBy(() -> service.acquire("session-1", "turn-2", "contact-1", "message-2"))
                .isInstanceOf(ActiveTurnLeaseService.LeaseUnavailableException.class);
        assertThatThrownBy(() -> service.withLease("session-1", "turn-2", "contact-1", "message-2", () -> "never"))
                .isInstanceOf(ActiveTurnLeaseService.LeaseUnavailableException.class);

        service.revoke(first);
        assertThat(gateway.findRunning("session-1", now.plusSeconds(1))).isEmpty();
    }

    @Test
    void requiresRunningLeaseAndChecksSessionTurnAndContactBindings() {
        FakeLeaseGateway gateway = new FakeLeaseGateway();
        ActiveTurnLeaseService service = new ActiveTurnLeaseService(gateway,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(1));
        service.acquire("session-1", "turn-1", "contact-1", "message-1");

        assertThat(service.requireActive("session-1", "turn-1", "contact-1", "message-1").status())
                .isEqualTo(ActiveTurnLeaseStatus.RUNNING);
        assertThatThrownBy(() -> service.requireActive("session-1", "other", "contact-1", "message-1"))
                .isInstanceOf(ActiveTurnLeaseService.LeaseRejectedException.class);
    }

    @Test
    void preservesSuccessfulActionWhenLeaseRevocationAlreadyLostTheRace() {
        FakeLeaseGateway gateway = new FakeLeaseGateway();
        ActiveTurnLeaseService service = new ActiveTurnLeaseService(gateway,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(30));

        String result = service.withLease("session-1", "turn-1", "contact-1", "message-1",
                () -> {
                    gateway.failRevocation = true;
                    return "persisted-success";
                });

        assertThat(result).isEqualTo("persisted-success");
    }

    @Test
    void holdsLeaseForAmbiguousHermesFailureInsteadOfRevokingIt() {
        FakeLeaseGateway gateway = new FakeLeaseGateway();
        ActiveTurnLeaseService service = new ActiveTurnLeaseService(gateway,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(30));

        assertThatThrownBy(() -> service.withLease("session-1", "turn-1", "contact-1", "message-1", () -> {
            throw br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesSessionsException
                    .ambiguous("ambiguous response");
        })).isInstanceOf(br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesSessionsException.class);

        assertThat(gateway.current("session-1")).isNotNull();
        assertThat(gateway.current("session-1").status()).isEqualTo(ActiveTurnLeaseStatus.RECONCILING);
        assertThat(gateway.current("session-1").turnId()).isEqualTo("turn-1");
    }

    @Test
    void refreshesLeaseDuringLongActionAndStopsHeartbeatsAfterFinally() throws Exception {
        FakeLeaseGateway gateway = new FakeLeaseGateway();
        ActiveTurnLeaseService service = new ActiveTurnLeaseService(gateway,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMillis(100));

        String result = service.withLease("session-1", "turn-1", "contact-1", "message-1", () -> {
            try {
                assertThat(gateway.firstHeartbeat.await(1, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("heartbeat did not arrive", interrupted);
            }
            return "completed";
        });

        int heartbeatsAfterCompletion = gateway.heartbeatCalls;
        Thread.sleep(150);

        assertThat(result).isEqualTo("completed");
        assertThat(heartbeatsAfterCompletion).isGreaterThanOrEqualTo(1);
        assertThat(gateway.heartbeatCalls).isEqualTo(heartbeatsAfterCompletion);
    }

    private static final class FakeLeaseGateway implements ActiveTurnLeaseGateway {
        private final ConcurrentMap<String, ActiveTurnLease> leases = new ConcurrentHashMap<>();
        private final CountDownLatch firstHeartbeat = new CountDownLatch(1);
        private volatile int heartbeatCalls;
        private boolean failRevocation;

        @Override
        public synchronized Optional<ActiveTurnLease> acquire(ActiveTurnLease requested) {
            ActiveTurnLease current = leases.get(requested.hermesSessionId());
            if (current != null && current.isActiveAt(requested.acquiredAt())) return Optional.empty();
            leases.put(requested.hermesSessionId(), requested);
            return Optional.of(requested);
        }

        @Override
        public Optional<ActiveTurnLease> findRunning(String sessionId, Instant at) {
            ActiveTurnLease value = leases.get(sessionId);
            return value != null && value.isActiveAt(at) ? Optional.of(value) : Optional.empty();
        }

        @Override
        public Optional<ActiveTurnLease> findBlocking(String sessionId, Instant at) {
            ActiveTurnLease value = leases.get(sessionId);
            return value != null && value.blocksNewTurnAt(at) ? Optional.of(value) : Optional.empty();
        }

        @Override
        public ActiveTurnLease markReconciling(String sessionId, String turnId, String claimToken, Instant at) {
            ActiveTurnLease value = leases.get(sessionId);
            if (value == null || !value.turnId().equals(turnId) || !value.claimToken().equals(claimToken)) {
                throw new IllegalArgumentException("lease claim binding mismatch");
            }
            ActiveTurnLease next = value.reconcile(at);
            leases.put(sessionId, next);
            return next;
        }

        @Override
        public ActiveTurnLease revoke(String sessionId, String turnId, Instant at) {
            if (failRevocation) {
                throw new IllegalArgumentException("lease expired before revocation");
            }
            ActiveTurnLease value = leases.get(sessionId).revoke(at);
            leases.put(sessionId, value);
            return value;
        }

        @Override
        public ActiveTurnLease expire(String sessionId, String turnId, Instant at) {
            ActiveTurnLease value = leases.get(sessionId).expire(at);
            leases.put(sessionId, value);
            return value;
        }

        @Override
        public synchronized ActiveTurnLease heartbeat(String sessionId, String turnId, String claimToken,
                                                       Instant at, Duration ttl) {
            heartbeatCalls++;
            firstHeartbeat.countDown();
            ActiveTurnLease value = leases.get(sessionId);
            ActiveTurnLease next = value.heartbeat(at, ttl);
            leases.put(sessionId, next);
            return next;
        }

        ActiveTurnLease current(String sessionId) {
            return leases.get(sessionId);
        }
    }
}
