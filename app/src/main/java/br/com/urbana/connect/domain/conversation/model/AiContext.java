package br.com.urbana.connect.domain.conversation.model;

import java.util.List;

public record AiContext(
        ConversationStep currentStep,
        String userMessage,
        List<ServiceSummary> availableServices,
        String conversationHistory) {
}
