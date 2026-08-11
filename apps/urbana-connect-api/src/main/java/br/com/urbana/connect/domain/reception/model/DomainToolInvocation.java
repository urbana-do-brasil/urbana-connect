package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;

public record DomainToolInvocation(
        String id,
        String idempotencyKey,
        String turnId,
        String hermesSessionId,
        String contactId,
        DomainToolName toolName,
        String argumentsHash,
        DomainToolInvocationStatus status,
        String resultCode,
        Object resultPayload,
        Instant createdAt,
        Instant finishedAt) {
    public DomainToolInvocation(String id, String idempotencyKey, String turnId, String hermesSessionId,
                                String contactId, DomainToolName toolName, String argumentsHash,
                                DomainToolInvocationStatus status, String resultCode,
                                Instant createdAt, Instant finishedAt) {
        this(id, idempotencyKey, turnId, hermesSessionId, contactId, toolName, argumentsHash,
                status, resultCode, null, createdAt, finishedAt);
    }

    public DomainToolInvocation {
        require(id, "id");
        require(idempotencyKey, "idempotencyKey");
        require(turnId, "turnId");
        require(hermesSessionId, "hermesSessionId");
        require(contactId, "contactId");
        if (toolName == null || argumentsHash == null || argumentsHash.isBlank()
                || status == null || createdAt == null) {
            throw new IllegalArgumentException("tool invocation fields are incomplete");
        }
    }

    public static String deriveIdempotencyKey(String turnId, DomainToolName toolName, String argumentsHash) {
        require(turnId, "turnId");
        require(argumentsHash, "argumentsHash");
        return turnId + ":" + toolName.wireName() + ":" + argumentsHash;
    }

    public DomainToolInvocation finish(DomainToolInvocationStatus nextStatus, String code, Instant now) {
        return finish(nextStatus, code, null, now);
    }

    public DomainToolInvocation finish(DomainToolInvocationStatus nextStatus, String code,
                                       Object payload, Instant now) {
        if (nextStatus == DomainToolInvocationStatus.STARTED) {
            throw new IllegalArgumentException("finished invocation cannot return to STARTED");
        }
        return new DomainToolInvocation(id, idempotencyKey, turnId, hermesSessionId, contactId,
                toolName, argumentsHash, nextStatus, code, payload, createdAt, now);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
