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
        Instant startedAt,
        Instant finishedAt,
        AgentUsage usage,
        String failureCode,
        AgentOutput output) {
    public ReceptionTurn(String id, String correlationId, String contactId, String hermesSessionId,
                         List<String> inboundMessageIds, ReceptionTurnStatus status, Instant startedAt,
                         Instant finishedAt, AgentUsage usage, String failureCode) {
        this(id, correlationId, contactId, hermesSessionId, inboundMessageIds, status, startedAt,
                finishedAt, usage, failureCode, null);
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
    }

    public ReceptionTurn start(Instant now) {
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.RUNNING, now, finishedAt, usage, failureCode, output);
    }

    public ReceptionTurn complete(AgentUsage turnUsage, Instant now) {
        return complete(turnUsage, now, output);
    }

    public ReceptionTurn complete(AgentUsage turnUsage, Instant now, AgentOutput completedOutput) {
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.COMPLETED, startedAt, now,
                turnUsage == null ? AgentUsage.empty() : turnUsage, null, completedOutput);
    }

    public ReceptionTurn fail(String code, Instant now) {
        require(code, "failureCode");
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.FAILED, startedAt, now, usage, code, output);
    }

    public ReceptionTurn blockByHuman(Instant now) {
        return new ReceptionTurn(id, correlationId, contactId, hermesSessionId, inboundMessageIds,
                ReceptionTurnStatus.BLOCKED_BY_HUMAN, startedAt, now, usage, null, output);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
