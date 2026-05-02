package br.com.urbana.connect.domain.conversation.model;

import java.util.List;
import java.util.Set;

public record StepContract(
        ConversationStep step,
        String goal,
        List<SlotRequirement> requiredSlots,
        List<SlotRequirement> optionalSlots,
        Set<ConversationalAiAction> allowedActions,
        Set<ConversationalAiAction> forbiddenActions,
        StepFallbackBehavior fallbackBehavior,
        int maxTurnsWithoutProgress,
        StructuredEscapeType structuredEscapeType,
        boolean deterministic) {

    public StepContract {
        requiredSlots = requiredSlots == null ? List.of() : List.copyOf(requiredSlots);
        optionalSlots = optionalSlots == null ? List.of() : List.copyOf(optionalSlots);
        allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        forbiddenActions = forbiddenActions == null ? Set.of() : Set.copyOf(forbiddenActions);
    }
}
