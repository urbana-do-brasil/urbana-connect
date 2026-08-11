package br.com.urbana.connect.domain.conversation.model;

import java.util.List;
import java.util.Map;

public record AssembledContext(
        ConversationStep currentStep,
        String stageGoal,
        String userMessage,
        String coreIdentity,
        String operationalPolicy,
        String conversationPlaybook,
        List<ServiceSummary> businessKnowledge,
        List<String> sessionMemory,
        Map<ConversationSlotName, ConversationSlotValue> slots,
        List<String> includedLayers) {

    public AssembledContext {
        businessKnowledge = businessKnowledge == null ? List.of() : List.copyOf(businessKnowledge);
        sessionMemory = sessionMemory == null ? List.of() : List.copyOf(sessionMemory);
        slots = slots == null ? Map.of() : Map.copyOf(slots);
        includedLayers = includedLayers == null ? List.of() : List.copyOf(includedLayers);
    }
}
