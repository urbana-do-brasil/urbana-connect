package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

/** In-process POC metrics; the snapshot can later be exported to Micrometer. */
public final class ReceptionMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReceptionMetrics.class);
    private static final String UNKNOWN_FAILURE = "UNKNOWN";
    private static final String UNCLASSIFIED_FAILURE = "UNCLASSIFIED";
    private static final String REDACTED = "REDACTED";
    private static final Pattern TECHNICAL_LABEL =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,79}");
    private static final Pattern TECHNICAL_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern TECHNICAL_FAILURE = Pattern.compile(
            "(?:HERMES|FAILED|HTTP|UPSTREAM|PROVIDER|LEASE|TURN|URBANA|UNKNOWN|UNCLASSIFIED)"
                    + "[A-Z0-9_.:-]*");
    private static final Pattern EXCEPTION_TYPE =
            Pattern.compile("[A-Za-z][A-Za-z0-9]*(?:Exception|Error)");
    private static final Pattern SENSITIVE_WORD = Pattern.compile(
            "(?i)(prompt|output|token|secret|password|credential|authorization|bearer|content)");

    private final LongAdder turns = new LongAdder();
    private final LongAdder completedTurns = new LongAdder();
    private final LongAdder failedTurns = new LongAdder();
    private final LongAdder humanBlockedTurns = new LongAdder();
    private final LongAdder delayedTurns = new LongAdder();
    private final LongAdder reconcilingTurns = new LongAdder();
    private final LongAdder safeRetryTurns = new LongAdder();
    private final LongAdder terminalFailedTurns = new LongAdder();
    private final LongAdder concurrentTurnBlocks = new LongAdder();
    private final LongAdder toolInvocations = new LongAdder();
    private final LongAdder durationMillis = new LongAdder();
    private final LongAdder inputTokens = new LongAdder();
    private final LongAdder outputTokens = new LongAdder();
    private final LongAdder totalTokens = new LongAdder();
    private final Map<String, LongAdder> failuresByCode = new ConcurrentHashMap<>();
    private final Set<String> recordedToolKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> recordedLeaseBlockKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> recordedTechnicalEventKeys = ConcurrentHashMap.newKeySet();

    public void recordTurn(ReceptionTurn turn) {
        if (turn == null) return;
        Duration duration = turn.startedAt() == null || turn.finishedAt() == null
                ? Duration.ZERO : Duration.between(turn.startedAt(), turn.finishedAt());
        // Test clocks and wall-clock adjustments can make an observed finish
        // precede the event timestamp; metrics must not break the turn path.
        if (duration.isNegative()) duration = Duration.ZERO;
        String failureClass = turn.failureClass() == null ? turn.failureCode() : turn.failureClass();
        recordTurn(duration, turn.usage(), 0, turn.status(), failureClass, turn.retryAllowed());
        recordTechnicalEvent("TURN_STATE", turn.correlationId(), turn.id(), turn.status().name(),
                turn.attempt(), duration.toMillis(), failureClass);
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
        boolean retryAllowed = status == ReceptionTurnStatus.FAILED_SAFE_TO_RETRY
                || (status == ReceptionTurnStatus.FAILED && "FAILED_RETRYABLE".equals(failureCode));
        recordTurn(duration, usage, tools, status, failureCode, retryAllowed);
    }

    private void recordTurn(Duration duration, AgentUsage usage, int tools,
                            ReceptionTurnStatus status, String failureCode,
                            boolean retryAllowed) {
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
        if (status == null) return;
        switch (status) {
            case COMPLETED -> completedTurns.increment();
            case DELAYED -> delayedTurns.increment();
            case RECONCILING -> reconcilingTurns.increment();
            case FAILED_SAFE_TO_RETRY -> {
                failedTurns.increment();
                safeRetryTurns.increment();
                recordFailure(failureCode);
            }
            case FAILED_TERMINAL -> {
                failedTurns.increment();
                terminalFailedTurns.increment();
                recordFailure(failureCode);
            }
            case FAILED -> {
                failedTurns.increment();
                if (retryAllowed) safeRetryTurns.increment();
                recordFailure(failureCode);
            }
            case BLOCKED_BY_HUMAN -> humanBlockedTurns.increment();
            default -> {
                // QUEUED and RUNNING are lifecycle states, not terminal
                // outcome counters. They remain available in technical logs.
            }
        }
    }

    private void recordFailure(String failureCode) {
        String code = safeFailureClass(failureCode);
        failuresByCode.computeIfAbsent(code, ignored -> new LongAdder()).increment();
    }

    /** Records one technical lease contention event; the same attempt is counted once. */
    public TechnicalEvent recordConcurrentTurnBlock(String correlationId, String turnId, int attempt) {
        String safeCorrelationId = safeTechnicalId(correlationId, "UNKNOWN");
        String safeTurnId = safeTechnicalId(turnId, "UNKNOWN");
        int safeAttempt = safeAttempt(attempt);
        String key = technicalEventKey("LEASE_BLOCKED", safeCorrelationId, safeTurnId,
                "BLOCKED", safeAttempt, "LEASE_BLOCKED");
        if (recordedLeaseBlockKeys.add(key)) {
            concurrentTurnBlocks.increment();
        }
        return recordTechnicalEvent("LEASE_BLOCKED", safeCorrelationId, safeTurnId,
                "BLOCKED", safeAttempt, 0L, "LEASE_BLOCKED");
    }

    /** Records a lifecycle event using only technical identifiers and a sanitized reason. */
    public TechnicalEvent recordTechnicalEvent(String correlationId, String turnId,
                                                ReceptionTurnStatus status, int attempt,
                                                String failureClass) {
        Objects.requireNonNull(status, "status");
        return recordTechnicalEvent("TURN_STATE", correlationId, turnId, status.name(),
                attempt, 0L, failureClass);
    }

    private TechnicalEvent recordTechnicalEvent(String eventType, String correlationId, String turnId,
                                                String status, int attempt, long durationMillis,
                                                String failureClass) {
        TechnicalEvent event = new TechnicalEvent(eventType, correlationId, turnId, status,
                attempt, durationMillis, failureClass);
        String key = technicalEventKey(event.eventType(), event.correlationId(), event.turnId(),
                event.status(), event.attempt(), event.failureClass());
        if (recordedTechnicalEventKeys.add(key)) {
            LOGGER.info("reception_technical_event eventType={} correlationId={} turnId={} "
                            + "status={} attempt={} durationMillis={} failureClass={}",
                    event.eventType(), event.correlationId(), event.turnId(), event.status(),
                    event.attempt(), event.durationMillis(), event.failureClass());
        }
        return event;
    }

    private static String technicalEventKey(String eventType, String correlationId, String turnId,
                                             String status, int attempt, String failureClass) {
        return String.join("|", eventType, correlationId, turnId, status,
                Integer.toString(attempt), failureClass);
    }

    private static int safeAttempt(int attempt) {
        return attempt < 1 ? 1 : attempt;
    }

    private static String safeFailureClass(String failureClass) {
        if (failureClass == null || failureClass.isBlank()) return UNKNOWN_FAILURE;
        String candidate = failureClass.trim();
        if (candidate.length() > 80 || SENSITIVE_WORD.matcher(candidate).find()) {
            return UNCLASSIFIED_FAILURE;
        }
        String normalized = candidate.toUpperCase(Locale.ROOT);
        if (TECHNICAL_FAILURE.matcher(normalized).matches()
                || EXCEPTION_TYPE.matcher(candidate).matches()) {
            return normalized;
        }
        return UNCLASSIFIED_FAILURE;
    }

    private static String safeTechnicalId(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String candidate = value.trim();
        if (candidate.length() > 128 || !TECHNICAL_ID.matcher(candidate).matches()
                || SENSITIVE_WORD.matcher(candidate).find()) {
            return REDACTED;
        }
        return candidate;
    }

    private static String safeTechnicalLabel(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String candidate = value.trim();
        if (candidate.length() > 80 || !TECHNICAL_LABEL.matcher(candidate).matches()
                || SENSITIVE_WORD.matcher(candidate).find()) {
            return fallback;
        }
        return candidate;
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
                totalTokens.sum(), failures, delayedTurns.sum(), reconcilingTurns.sum(),
                safeRetryTurns.sum(), terminalFailedTurns.sum(), concurrentTurnBlocks.sum());
    }

    public record Snapshot(long turns, long completedTurns, long failedTurns, long humanBlockedTurns,
                           long toolInvocations, long durationMillis, long inputTokens,
                           long outputTokens, long totalTokens, Map<String, Long> failuresByCode,
                           long delayedTurns, long reconcilingTurns, long safeRetryTurns,
                           long terminalFailedTurns, long concurrentTurnBlocks) {
        /** Compatibility constructor for the original metrics snapshot shape. */
        public Snapshot(long turns, long completedTurns, long failedTurns, long humanBlockedTurns,
                        long toolInvocations, long durationMillis, long inputTokens,
                        long outputTokens, long totalTokens, Map<String, Long> failuresByCode) {
            this(turns, completedTurns, failedTurns, humanBlockedTurns, toolInvocations,
                    durationMillis, inputTokens, outputTokens, totalTokens, failuresByCode,
                    0, 0, 0, 0, 0);
        }

        public Snapshot {
            failuresByCode = Map.copyOf(failuresByCode == null ? Map.of() : failuresByCode);
        }

        public double averageDurationMillis() {
            return turns == 0 ? 0d : (double) durationMillis / turns;
        }
    }

    /** Safe, structured observability payload; it cannot carry conversation text. */
    public record TechnicalEvent(String eventType, String correlationId, String turnId,
                                 String status, int attempt, long durationMillis,
                                 String failureClass) {
        public TechnicalEvent {
            eventType = safeTechnicalLabel(eventType, "TECHNICAL_EVENT");
            correlationId = safeTechnicalId(correlationId, "UNKNOWN");
            turnId = safeTechnicalId(turnId, "UNKNOWN");
            status = safeTechnicalLabel(status, "UNKNOWN");
            attempt = safeAttempt(attempt);
            durationMillis = Math.max(0L, durationMillis);
            failureClass = safeFailureClass(failureClass);
        }
    }
}
