package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReceptionMetricsTest {
    @Test
    void recordsTurnToolDurationUsageAndFailureWithoutExposingContent() {
        ReceptionMetrics metrics = new ReceptionMetrics();
        metrics.recordTurn(Duration.ofMillis(120), new AgentUsage(4, 6), 2,
                ReceptionTurnStatus.COMPLETED, null);
        metrics.recordTurn(Duration.ofMillis(80), AgentUsage.empty(), 1,
                ReceptionTurnStatus.FAILED, "HERMES_TIMEOUT");

        ReceptionMetrics.Snapshot snapshot = metrics.snapshot();

        assertThat(snapshot.turns()).isEqualTo(2);
        assertThat(snapshot.completedTurns()).isEqualTo(1);
        assertThat(snapshot.failedTurns()).isEqualTo(1);
        assertThat(snapshot.toolInvocations()).isEqualTo(3);
        assertThat(snapshot.durationMillis()).isEqualTo(200);
        assertThat(snapshot.totalTokens()).isEqualTo(10);
        assertThat(snapshot.failuresByCode()).containsEntry("HERMES_TIMEOUT", 1L);
        assertThat(snapshot.toString()).doesNotContain("secret", "prompt");
    }

    @Test
    void reportsHumanBlockedTurnsAndAverageDuration() {
        ReceptionMetrics metrics = new ReceptionMetrics();
        metrics.recordTurn(Duration.ofMillis(50), AgentUsage.empty(), 0,
                ReceptionTurnStatus.BLOCKED_BY_HUMAN, null);

        assertThat(metrics.snapshot().humanBlockedTurns()).isEqualTo(1);
        assertThat(metrics.snapshot().averageDurationMillis()).isEqualTo(50d);
    }

    @Test
    void countsTheSameDurableToolInvocationOnlyOnceAcrossLedgerAndExecutionHooks() {
        ReceptionMetrics metrics = new ReceptionMetrics();

        metrics.recordToolInvocation("tool-key-1");
        metrics.recordToolInvocation("tool-key-1");
        metrics.recordToolInvocation("tool-key-2");

        assertThat(metrics.snapshot().toolInvocations()).isEqualTo(2);
    }

    @Test
    void recordsResilienceStateCountersAndDeduplicatesConcurrentLeaseBlocks() {
        ReceptionMetrics metrics = new ReceptionMetrics();

        metrics.recordTurn(Duration.ofMillis(10), AgentUsage.empty(), 0,
                ReceptionTurnStatus.DELAYED, "HERMES_SLOW");
        metrics.recordTurn(Duration.ofMillis(20), AgentUsage.empty(), 0,
                ReceptionTurnStatus.RECONCILING, "HERMES_TIMEOUT_AFTER_DISPATCH");
        metrics.recordTurn(Duration.ofMillis(30), AgentUsage.empty(), 0,
                ReceptionTurnStatus.FAILED_SAFE_TO_RETRY, "HERMES_REJECTED_BEFORE_DISPATCH");
        metrics.recordTurn(Duration.ofMillis(40), AgentUsage.empty(), 0,
                ReceptionTurnStatus.FAILED_TERMINAL, "HERMES_REMOTE_TERMINAL");
        metrics.recordConcurrentTurnBlock("correlation-1", "turn-1", 1);
        metrics.recordConcurrentTurnBlock("correlation-1", "turn-1", 1);

        ReceptionMetrics.Snapshot snapshot = metrics.snapshot();

        assertThat(snapshot.delayedTurns()).isEqualTo(1);
        assertThat(snapshot.reconcilingTurns()).isEqualTo(1);
        assertThat(snapshot.safeRetryTurns()).isEqualTo(1);
        assertThat(snapshot.terminalFailedTurns()).isEqualTo(1);
        assertThat(snapshot.failedTurns()).isEqualTo(2);
        assertThat(snapshot.concurrentTurnBlocks()).isEqualTo(1);
    }

    @Test
    void keepsSnapshotsAndTechnicalEventsFreeOfConversationalTextAndSecrets() {
        ReceptionMetrics metrics = new ReceptionMetrics();

        metrics.recordTurn(Duration.ofMillis(15), AgentUsage.empty(), 0,
                ReceptionTurnStatus.FAILED_TERMINAL,
                "customer prompt secret=do-not-log");
        ReceptionMetrics.TechnicalEvent event = metrics.recordTechnicalEvent(
                "correlation-2", "turn-2", ReceptionTurnStatus.FAILED_TERMINAL, 2,
                "provider output secret=do-not-log");

        assertThat(metrics.snapshot().toString())
                .doesNotContain("customer prompt", "provider output", "secret=do-not-log");
        assertThat(event.toString())
                .doesNotContain("customer prompt", "provider output", "secret=do-not-log");
        assertThat(event.failureClass()).isEqualTo("UNCLASSIFIED");
    }
}
