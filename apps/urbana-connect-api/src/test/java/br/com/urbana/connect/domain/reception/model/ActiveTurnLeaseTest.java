package br.com.urbana.connect.domain.reception.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveTurnLeaseTest {
    private static final Instant ACQUIRED = Instant.parse("2026-08-31T12:00:00Z");
    private static final Instant EXPIRES = ACQUIRED.plusSeconds(30);

    @Test
    void compatibilityConstructorsCreateCanonicalClaimAndProvenance() {
        ActiveTurnLease legacy = new ActiveTurnLease("session-1", "turn-1", "contact-1", "message-1",
                ActiveTurnLeaseStatus.RUNNING, ACQUIRED, EXPIRES, null, 2);
        ActiveTurnLease withClaim = new ActiveTurnLease("session-1", "turn-2", "contact-1", "message-2",
                ActiveTurnLeaseStatus.RUNNING, ACQUIRED, EXPIRES, null, 3, "claim-2");

        assertThat(legacy.claimToken()).isEqualTo("turn-1:2");
        assertThat(legacy.sourceMessageIds()).containsExactly("message-1");
        assertThat(withClaim.claimToken()).isEqualTo("claim-2");
        assertThat(withClaim.sourceMessageIds()).containsExactly("message-2");
    }

    @Test
    void rejectsIncompleteOrInconsistentLeaseState() {
        List<String> sourceMessageIds = List.of("message");
        List<String> emptySourceMessageIds = List.of();
        List<String> blankSourceMessageIds = List.of(" ");
        assertThatThrownBy(() -> new ActiveTurnLease(null, "turn", "contact", "message",
                ActiveTurnLeaseStatus.RUNNING, ACQUIRED, EXPIRES, null, 0, "claim", sourceMessageIds))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActiveTurnLease("session", "turn", "contact", "message", null,
                ACQUIRED, EXPIRES, null, 0, "claim", sourceMessageIds))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("status");
        assertThatThrownBy(() -> new ActiveTurnLease("session", "turn", "contact", "message",
                ActiveTurnLeaseStatus.RUNNING, ACQUIRED, ACQUIRED, null, 0, "claim", sourceMessageIds))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expiresAt");
        assertThatThrownBy(() -> new ActiveTurnLease("session", "turn", "contact", "message",
                ActiveTurnLeaseStatus.RUNNING, ACQUIRED, EXPIRES, null, -1, "claim", sourceMessageIds))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> new ActiveTurnLease("session", "turn", "contact", "message",
                ActiveTurnLeaseStatus.RUNNING, ACQUIRED, EXPIRES, null, 0, "claim", emptySourceMessageIds))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sourceMessageIds");
        assertThatThrownBy(() -> new ActiveTurnLease("session", "turn", "contact", "message",
                ActiveTurnLeaseStatus.RUNNING, ACQUIRED, EXPIRES, null, 0, "claim", blankSourceMessageIds))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sourceMessageIds");
    }

    @Test
    void evaluatesRunningAndReconcilingLeaseOwnershipAtTheBoundary() {
        ActiveTurnLease running = lease(ActiveTurnLeaseStatus.RUNNING);
        ActiveTurnLease reconciling = lease(ActiveTurnLeaseStatus.RECONCILING);
        ActiveTurnLease expired = lease(ActiveTurnLeaseStatus.EXPIRED);

        assertThat(running.isActiveAt(ACQUIRED.plusSeconds(29))).isTrue();
        assertThat(running.isActiveAt(EXPIRES)).isFalse();
        assertThat(running.blocksNewTurnAt(ACQUIRED.plusSeconds(1))).isTrue();
        assertThat(running.blocksNewTurnAt(EXPIRES)).isFalse();
        assertThat(reconciling.isActiveAt(ACQUIRED.plusSeconds(1))).isFalse();
        assertThat(reconciling.blocksNewTurnAt(ACQUIRED.plusSeconds(1))).isTrue();
        assertThat(expired.blocksNewTurnAt(ACQUIRED.plusSeconds(1))).isFalse();
    }

    @Test
    void transitionsRunningAndReconcilingLeasesIdempotently() {
        ActiveTurnLease running = lease(ActiveTurnLeaseStatus.RUNNING);
        ActiveTurnLease reconciling = running.reconcile(ACQUIRED.plusSeconds(2));
        ActiveTurnLease revoked = reconciling.revoke(ACQUIRED.plusSeconds(3));

        assertThat(reconciling.status()).isEqualTo(ActiveTurnLeaseStatus.RECONCILING);
        assertThat(reconciling.version()).isEqualTo(1);
        assertThat(revoked.status()).isEqualTo(ActiveTurnLeaseStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(ACQUIRED.plusSeconds(3));
        assertThat(revoked.version()).isEqualTo(2);
        assertThat(revoked.revoke(ACQUIRED.plusSeconds(4))).isSameAs(revoked);
        assertThat(lease(ActiveTurnLeaseStatus.REVOKED).reconcile(ACQUIRED.plusSeconds(1)))
                .isEqualTo(lease(ActiveTurnLeaseStatus.REVOKED));
    }

    @Test
    void expiresOnlyRunningLeasesAfterTheirDeadline() {
        ActiveTurnLease running = lease(ActiveTurnLeaseStatus.RUNNING);
        ActiveTurnLease beforeDeadline = running.expire(EXPIRES.minusNanos(1));
        ActiveTurnLease expired = running.expire(EXPIRES);
        ActiveTurnLease alreadyReconciled = lease(ActiveTurnLeaseStatus.RECONCILING).expire(EXPIRES.plusSeconds(1));

        assertThat(beforeDeadline).isSameAs(running);
        assertThat(expired.status()).isEqualTo(ActiveTurnLeaseStatus.EXPIRED);
        assertThat(expired.version()).isEqualTo(1);
        assertThat(expired.revokedAt()).isNull();
        assertThat(alreadyReconciled).isEqualTo(lease(ActiveTurnLeaseStatus.RECONCILING));
    }

    @Test
    void heartbeatsOnlyRunningOrReconcilingLeasesAndRequiresPositiveTtl() {
        ActiveTurnLease running = lease(ActiveTurnLeaseStatus.RUNNING);
        ActiveTurnLease reconciling = lease(ActiveTurnLeaseStatus.RECONCILING);

        assertThatThrownBy(() -> running.heartbeat(ACQUIRED, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> running.heartbeat(ACQUIRED, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        Duration negativeTtl = Duration.ofSeconds(-1);
        assertThatThrownBy(() -> running.heartbeat(ACQUIRED, negativeTtl)).isInstanceOf(IllegalArgumentException.class);

        ActiveTurnLease extended = running.heartbeat(ACQUIRED.plusSeconds(5), Duration.ofMinutes(1));
        assertThat(extended.status()).isEqualTo(ActiveTurnLeaseStatus.RUNNING);
        assertThat(extended.expiresAt()).isEqualTo(ACQUIRED.plusSeconds(65));
        assertThat(extended.version()).isEqualTo(1);
        assertThat(reconciling.heartbeat(ACQUIRED.plusSeconds(5), Duration.ofSeconds(10)).status())
                .isEqualTo(ActiveTurnLeaseStatus.RECONCILING);
        assertThat(lease(ActiveTurnLeaseStatus.EXPIRED).heartbeat(ACQUIRED.plusSeconds(5), Duration.ofSeconds(10)))
                .isEqualTo(lease(ActiveTurnLeaseStatus.EXPIRED));
    }

    private static ActiveTurnLease lease(ActiveTurnLeaseStatus status) {
        return new ActiveTurnLease("session-1", "turn-1", "contact-1", "message-1", status,
                ACQUIRED, EXPIRES, status == ActiveTurnLeaseStatus.REVOKED ? ACQUIRED : null, 0,
                "claim-1", List.of("message-1"));
    }
}
