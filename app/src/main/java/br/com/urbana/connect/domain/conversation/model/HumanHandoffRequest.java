package br.com.urbana.connect.domain.conversation.model;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;

import java.time.Instant;

public record HumanHandoffRequest(
        String phoneNumber,
        ConversationStep currentStep,
        ServiceType selectedService,
        String paymentMethod,
        String latestMessage,
        Instant receivedAt) {
}
