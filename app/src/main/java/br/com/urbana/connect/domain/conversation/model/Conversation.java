package br.com.urbana.connect.domain.conversation.model;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record Conversation(
        String id,
        String phoneNumber,
        ConversationStatus status,
        ConversationStep currentStep,
        ServiceType selectedService,
        ConversationContext context,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    public static Conversation start(String phoneNumber, Instant now) {
        return new Conversation(
            null,
            phoneNumber,
            ConversationStatus.ACTIVE,
            ConversationStep.GREETING,
            null,
            ConversationContext.empty(),
            now,
            now,
            now.plus(24, ChronoUnit.HOURS)
        );
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public Conversation expire(Instant now) {
        return new Conversation(
            id,
            phoneNumber,
            ConversationStatus.EXPIRED,
            currentStep,
            selectedService,
            context,
            createdAt,
            now,
            expiresAt
        );
    }

    public Conversation moveTo(ConversationStep step, Instant now) {
        return new Conversation(
            id,
            phoneNumber,
            status,
            step,
            selectedService,
            context,
            createdAt,
            now,
            expiresAt
        );
    }

    public Conversation selectService(ServiceType serviceType, ConversationStep nextStep, Instant now) {
        return new Conversation(
            id,
            phoneNumber,
            status,
            nextStep,
            serviceType,
            context,
            createdAt,
            now,
            expiresAt
        );
    }

    public Conversation selectPaymentMethod(String paymentMethod, ConversationStep nextStep, Instant now) {
        return new Conversation(
            id,
            phoneNumber,
            status,
            nextStep,
            selectedService,
            context.withPaymentMethod(paymentMethod),
            createdAt,
            now,
            expiresAt
        );
    }
}
