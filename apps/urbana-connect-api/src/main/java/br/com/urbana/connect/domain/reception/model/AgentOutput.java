package br.com.urbana.connect.domain.reception.model;

import java.util.Objects;

public record AgentOutput(String message, AgentNextAction nextAction, String handoffReason) {
    public AgentOutput {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must contain non-blank text");
        }
        nextAction = Objects.requireNonNull(nextAction, "nextAction");
        if (nextAction == AgentNextAction.HANDOFF && (handoffReason == null || handoffReason.isBlank())) {
            throw new IllegalArgumentException("handoffReason is required for HANDOFF");
        }
        if (handoffReason != null && handoffReason.length() > 500) {
            throw new IllegalArgumentException("handoffReason is too long");
        }
        if (nextAction != AgentNextAction.HANDOFF && handoffReason != null) {
            throw new IllegalArgumentException("handoffReason is only valid for HANDOFF");
        }
    }

    public AgentOutput(String message, AgentNextAction nextAction) {
        this(message, nextAction, null);
    }
}
