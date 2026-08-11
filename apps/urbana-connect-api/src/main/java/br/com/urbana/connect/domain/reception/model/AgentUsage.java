package br.com.urbana.connect.domain.reception.model;

public record AgentUsage(long inputTokens, long outputTokens, long totalTokens) {
    public AgentUsage {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token usage cannot be negative");
        }
    }

    public AgentUsage(long inputTokens, long outputTokens) {
        this(inputTokens, outputTokens, inputTokens + outputTokens);
    }

    public static AgentUsage empty() {
        return new AgentUsage(0, 0, 0);
    }
}
