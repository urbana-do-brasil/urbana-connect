package br.com.urbana.connect.domain.conversation.model;

import java.util.List;
import java.util.Map;

public record AiContext(
        ConversationStep currentStep,
        String userMessage,
        List<ServiceSummary> availableServices,
        String conversationHistory,
        Map<ConversationSlotName, ConversationSlotValue> slots) {

    public AiContext(
            ConversationStep currentStep,
            String userMessage,
            List<ServiceSummary> availableServices,
            String conversationHistory) {
        this(currentStep, userMessage, availableServices, conversationHistory, Map.of());
    }
}
