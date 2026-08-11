package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReceptionTurn(
        String id,
        String correlationId,
        String contactId,
        String hermesSessionId,
        List<String> inboundMessageIds,
        ReceptionTurnStatus status,
        Instant acceptedAt,
        Instant startedAt,
        Instant finishedAt,
        int attempt,
        String failureClass,
        boolean retryAllowed,
        String historyCheckpoint,
        long version,
        AgentUsage usage,
        String failureCode,
        AgentOutput output) {

    /** Compatibility constructor for the pre-resilience persistence shape. */
    public ReceptionTurn(String id, String correlationId, String contactId, String hermesSessionId,
                         List<String> inboundMessageIds, ReceptionTurnStatus status, Instant startedAt,
                         Instant finishedAt, AgentUsage usage, String failureCode) {
        this(id, correlationId, contactId, hermesSessionId, inboundMessageIds, status, startedAt,
                startedAt, finishedAt, 1, failureCode, false, null, 0, usage, failureCode, null);
    }

    /** Compatibility constructor for the pre-resilience persistence shape. */
    public ReceptionTurn(String id, String correlationId, String contactId, String hermesSessionId,
                         List<String> inboundMessageIds, ReceptionTurnStatus status, Instant startedAt,
                         Instant finishedAt, AgentUsage usage, String failureCode, AgentOutput output) {
        this(id, correlationId, contactId, hermesSessionId, inboundMessageIds, status, startedAt,
                startedAt, finishedAt, 1, failureCode, false, null, 0, usage, failureCode, output);
    }

    public ReceptionTurn {
        require(id, "id");
        require(correlationId, "correlationId");
        require(contactId, "contactId");
        require(hermesSessionId, "hermesSessionId");
        inboundMessageIds = List.copyOf(Objects.requireNonNull(inboundMessageIds, "inboundMessageIds"));
        if (inboundMessageIds.isEmpty()) {
            throw new IllegalArgumentException("at least one inbound message is required");
        }
        status = Objects.requireNonNull(status, "status");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        usage = usage == null ? AgentUsage.empty() : usage;
    }

    public static ReceptionTurn queued(String id, String correlationId, String contactId,
                                       String hermesSessionId, List<String> inboundMessageIds,
                                       Instant acceptedAt, String historyCheckpoint) {
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.QUEUED, acceptedAt, null, null, 1, null, false,
                historyCheckpoint, 0, AgentUsage.empty(), null, null);
    }

    public ReceptionTurn start(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isTerminal()) {
            return this;
        }
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.RUNNING, acceptedAt == null ? now : acceptedAt, now, null,
                attempt, null, false, historyCheckpoint, version + 1, usage, null, output);
    }

    public ReceptionTurn delay(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isTerminal() || status == ReceptionTurnStatus.RECONCILING) {
            return this;
        }
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.DELAYED, acceptedAt, startedAt, null, attempt, failureClass,
                false, historyCheckpoint, version + 1, usage, failureCode, output);
    }

    public ReceptionTurn reconcile(String failureClass, Instant now) {
        require(failureClass, "failureClass");
        Objects.requireNonNull(now, "now");
        if (isTerminal()) {
            return this;
        }
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.RECONCILING, acceptedAt, startedAt, null, attempt, failureClass,
                false, historyCheckpoint, version + 1, usage, failureClass, output);
    }

    public ReceptionTurn complete(AgentUsage turnUsage, Instant now) {
        return complete(turnUsage, now, output);
    }

    public ReceptionTurn complete(AgentUsage turnUsage, Instant now, AgentOutput completedOutput) {
        Objects.requireNonNull(now, "now");
        if (isTerminal()) {
            return this;
        }
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.COMPLETED, acceptedAt, startedAt, now, attempt, null, false,
                historyCheckpoint, version + 1, turnUsage, null, completedOutput);
    }

    public ReceptionTurn fail(String code, Instant now) {
        require(code, "failureCode");
        Objects.requireNonNull(now, "now");
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.FAILED, acceptedAt, startedAt, now, attempt, code,
                "FAILED_RETRYABLE".equals(code), historyCheckpoint, version + 1, usage, code, output);
    }

    public ReceptionTurn failSafeToRetry(String code, Instant now) {
        require(code, "failureClass");
        Objects.requireNonNull(now, "now");
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.FAILED_SAFE_TO_RETRY, acceptedAt, startedAt, now, attempt,
                code, true, historyCheckpoint, version + 1, usage, code, output);
    }

    public ReceptionTurn failTerminal(String code, Instant now) {
        require(code, "failureClass");
        Objects.requireNonNull(now, "now");
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.FAILED_TERMINAL, acceptedAt, startedAt, now, attempt,
                code, false, historyCheckpoint, version + 1, usage, code, output);
    }

    public ReceptionTurn retry(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!retryAllowed || (status != ReceptionTurnStatus.FAILED_SAFE_TO_RETRY
                && status != ReceptionTurnStatus.FAILED)) {
            throw new IllegalStateException("turn is not safe to retry");
        }
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.QUEUED, acceptedAt == null ? now : acceptedAt, null, null,
                attempt + 1, null, false, historyCheckpoint, version + 1, AgentUsage.empty(), null, null);
    }

    public ReceptionTurn blockByHuman(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isTerminal()) {
            return this;
        }
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.BLOCKED_BY_HUMAN, acceptedAt, startedAt, now, attempt, null,
                false, historyCheckpoint, version + 1, usage, null, output);
    }

    public boolean isTerminal() {
        return status == ReceptionTurnStatus.COMPLETED
                || status == ReceptionTurnStatus.FAILED_SAFE_TO_RETRY
                || status == ReceptionTurnStatus.FAILED_TERMINAL
                || status == ReceptionTurnStatus.FAILED
                || status == ReceptionTurnStatus.BLOCKED_BY_HUMAN;
    }

    public boolean isActiveOrUncertain() {
        return status == ReceptionTurnStatus.QUEUED
                || status == ReceptionTurnStatus.RUNNING
                || status == ReceptionTurnStatus.DELAYED
                || status == ReceptionTurnStatus.RECONCILING;
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
