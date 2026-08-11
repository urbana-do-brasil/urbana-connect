package br.com.urbana.connect.domain.reception.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceptionTurnTest {
    private static final Instant ACCEPTED = Instant.parse("2026-08-07T12:00:00Z");

    @Test
    void keepsAnAmbiguousTurnGatedThroughDelayAndReconciliation() {
        ReceptionTurn queued = ReceptionTurn.queued("turn-1", "corr-1", "contact-1", "session-1",
                List.of("message-1"), ACCEPTED, "cursor-1|1");

        ReceptionTurn running = queued.start(ACCEPTED.plusSeconds(1));
        ReceptionTurn delayed = running.delay(ACCEPTED.plusSeconds(31));
        ReceptionTurn reconciling = delayed.reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", ACCEPTED.plusSeconds(32));

        assertThat(running.status()).isEqualTo(ReceptionTurnStatus.RUNNING);
        assertThat(delayed.status()).isEqualTo(ReceptionTurnStatus.DELAYED);
        assertThat(reconciling.status()).isEqualTo(ReceptionTurnStatus.RECONCILING);
        assertThat(reconciling.retryAllowed()).isFalse();
        assertThat(reconciling.historyCheckpoint()).isEqualTo("cursor-1|1");
        assertThat(reconciling.finishedAt()).isNull();
    }

    @Test
    void onlyAProvenPreDispatchFailureEnablesRetryAndIncrementsAttempt() {
        ReceptionTurn turn = ReceptionTurn.queued("turn-2", "corr-2", "contact-1", "session-1",
                List.of("message-2"), ACCEPTED, null).start(ACCEPTED.plusSeconds(1));

        ReceptionTurn failed = turn.failSafeToRetry("HERMES_REJECTED_BEFORE_DISPATCH", ACCEPTED.plusSeconds(2));
        ReceptionTurn retry = failed.retry(ACCEPTED.plusSeconds(3));

        assertThat(failed.status()).isEqualTo(ReceptionTurnStatus.FAILED_SAFE_TO_RETRY);
        assertThat(failed.retryAllowed()).isTrue();
        assertThat(retry.status()).isEqualTo(ReceptionTurnStatus.QUEUED);
        assertThat(retry.attempt()).isEqualTo(2);
        assertThat(retry.retryAllowed()).isFalse();
    }

    @Test
    void completionIsIdempotentAndDoesNotReopenAResolvedTurn() {
        ReceptionTurn turn = ReceptionTurn.queued("turn-3", "corr-3", "contact-1", "session-1",
                List.of("message-3"), ACCEPTED, null).start(ACCEPTED.plusSeconds(1));
        AgentOutput output = new AgentOutput("ok", AgentNextAction.AWAIT_CUSTOMER);

        ReceptionTurn completed = turn.complete(AgentUsage.empty(), ACCEPTED.plusSeconds(2), output);
        ReceptionTurn repeated = completed.complete(AgentUsage.empty(), ACCEPTED.plusSeconds(3), output);

        assertThat(repeated).isEqualTo(completed);
        assertThat(repeated.retryAllowed()).isFalse();
    }
}
