package br.com.urbana.connect.domain.conversation.model;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;

import java.time.Instant;
import java.util.List;

public record HumanHandoffRequest(
        String phoneNumber,
        ConversationStep currentStep,
        ServiceType selectedService,
        String paymentMethod,
        List<String> recentMessages,
        Instant receivedAt) {
}
