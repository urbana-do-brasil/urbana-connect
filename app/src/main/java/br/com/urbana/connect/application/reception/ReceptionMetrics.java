package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** In-process POC metrics; the snapshot can later be exported to Micrometer. */
public final class ReceptionMetrics {
    private final LongAdder turns = new LongAdder();
    private final LongAdder completedTurns = new LongAdder();
    private final LongAdder failedTurns = new LongAdder();
    private final LongAdder humanBlockedTurns = new LongAdder();
    private final LongAdder toolInvocations = new LongAdder();
    private final LongAdder durationMillis = new LongAdder();
    private final LongAdder inputTokens = new LongAdder();
    private final LongAdder outputTokens = new LongAdder();
    private final LongAdder totalTokens = new LongAdder();
    private final Map<String, LongAdder> failuresByCode = new ConcurrentHashMap<>();
    private final Set<String> recordedToolKeys = ConcurrentHashMap.newKeySet();

    public void recordTurn(ReceptionTurn turn) {
        if (turn == null) return;
        Duration duration = turn.startedAt() == null || turn.finishedAt() == null
                ? Duration.ZERO : Duration.between(turn.startedAt(), turn.finishedAt());
        // Test clocks and wall-clock adjustments can make an observed finish
        // precede the event timestamp; metrics must not break the turn path.
        if (duration.isNegative()) duration = Duration.ZERO;
        recordTurn(duration, turn.usage(), 0, turn.status(), turn.failureCode());
    }

    public void recordTurn(ReceptionTurn turn, Collection<DomainToolInvocation> ledger) {
        if (turn == null) return;
        if (ledger != null) {
            ledger.stream().filter(invocation -> invocation != null)
                    .forEach(invocation -> recordToolInvocation(invocation.idempotencyKey()));
        }
        recordTurn(turn);
    }

    public void recordTurn(Duration duration, AgentUsage usage, int tools,
                           ReceptionTurnStatus status, String failureCode) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        if (tools < 0) throw new IllegalArgumentException("tools must not be negative");
        turns.increment();
        durationMillis.add(duration.toMillis());
        toolInvocations.add(tools);
        AgentUsage safeUsage = usage == null ? AgentUsage.empty() : usage;
        inputTokens.add(safeUsage.inputTokens());
        outputTokens.add(safeUsage.outputTokens());
        totalTokens.add(safeUsage.totalTokens());
        if (status == ReceptionTurnStatus.COMPLETED) completedTurns.increment();
        if (status == ReceptionTurnStatus.FAILED) {
            failedTurns.increment();
            String code = failureCode == null || failureCode.isBlank() ? "UNKNOWN" : failureCode;
            failuresByCode.computeIfAbsent(code, ignored -> new LongAdder()).increment();
        }
        if (status == ReceptionTurnStatus.BLOCKED_BY_HUMAN) humanBlockedTurns.increment();
    }

    public void recordToolInvocation() {
        toolInvocations.increment();
    }

    public void recordToolInvocation(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            recordToolInvocation();
            return;
        }
        if (recordedToolKeys.add(idempotencyKey)) {
            toolInvocations.increment();
        }
    }

    public Snapshot snapshot() {
        Map<String, Long> failures = failuresByCode.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> entry.getValue().sum()));
        return new Snapshot(turns.sum(), completedTurns.sum(), failedTurns.sum(), humanBlockedTurns.sum(),
                toolInvocations.sum(), durationMillis.sum(), inputTokens.sum(), outputTokens.sum(),
                totalTokens.sum(), failures);
    }

    public record Snapshot(long turns, long completedTurns, long failedTurns, long humanBlockedTurns,
                           long toolInvocations, long durationMillis, long inputTokens,
                           long outputTokens, long totalTokens, Map<String, Long> failuresByCode) {
        public Snapshot {
            failuresByCode = Map.copyOf(failuresByCode);
        }

        public double averageDurationMillis() {
            return turns == 0 ? 0d : (double) durationMillis / turns;
        }
    }
}
