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
}
