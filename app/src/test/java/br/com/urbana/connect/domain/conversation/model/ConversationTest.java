package br.com.urbana.connect.domain.conversation.model;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    void shouldStartConversationWithGreetingAndDefaultExpiration() {
        Instant now = Instant.parse("2026-04-06T10:00:00Z");

        Conversation conversation = Conversation.start("+5583999999999", now);

        assertThat(conversation.id()).isNull();
        assertThat(conversation.phoneNumber()).isEqualTo("+5583999999999");
        assertThat(conversation.status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(conversation.currentStep()).isEqualTo(ConversationStep.GREETING);
        assertThat(conversation.selectedService()).isNull();
        assertThat(conversation.context()).isEqualTo(ConversationContext.empty());
        assertThat(conversation.createdAt()).isEqualTo(now);
        assertThat(conversation.updatedAt()).isEqualTo(now);
        assertThat(conversation.expiresAt()).isEqualTo(now.plusSeconds(24 * 60 * 60));
    }

    @Test
    void shouldConsiderConversationExpiredWhenInstantReachesExpiration() {
        Instant createdAt = Instant.parse("2026-04-06T10:00:00Z");
        Conversation conversation = Conversation.start("+5583999999999", createdAt);

        assertThat(conversation.isExpiredAt(createdAt.plusSeconds(24 * 60 * 60 - 1))).isFalse();
        assertThat(conversation.isExpiredAt(createdAt.plusSeconds(24 * 60 * 60))).isTrue();
    }

    @Test
    void shouldExpireConversationWithoutChangingConfiguredExpiration() {
        Instant createdAt = Instant.parse("2026-04-06T10:00:00Z");
        Instant expiredAt = createdAt.plusSeconds(24 * 60 * 60);
        Conversation conversation = new Conversation(
            "conv-1",
            "+5583999999999",
            ConversationStatus.ACTIVE,
            ConversationStep.AWAITING_TERMS,
            ServiceType.DECOR,
            new ConversationContext("PIX"),
            createdAt,
            createdAt.plusSeconds(300),
            expiredAt
        );

        Conversation expired = conversation.expire(expiredAt.plusSeconds(1));

        assertThat(expired.status()).isEqualTo(ConversationStatus.EXPIRED);
        assertThat(expired.currentStep()).isEqualTo(ConversationStep.AWAITING_TERMS);
        assertThat(expired.selectedService()).isEqualTo(ServiceType.DECOR);
        assertThat(expired.context().paymentMethod()).isEqualTo("PIX");
        assertThat(expired.updatedAt()).isEqualTo(expiredAt.plusSeconds(1));
        assertThat(expired.expiresAt()).isEqualTo(expiredAt);
    }

    @Test
    void shouldMoveConversationToNewStepKeepingCurrentSelectionAndContext() {
        Instant createdAt = Instant.parse("2026-04-06T10:00:00Z");
        Conversation conversation = new Conversation(
            "conv-1",
            "+5583999999999",
            ConversationStatus.ACTIVE,
            ConversationStep.TRIAGE_DIRECT,
            ServiceType.DECOR,
            new ConversationContext("PIX"),
            createdAt,
            createdAt.plusSeconds(60),
            createdAt.plusSeconds(24 * 60 * 60)
        );

        Conversation moved = conversation.moveTo(ConversationStep.AWAITING_CONFIRMATION, createdAt.plusSeconds(120));

        assertThat(moved.currentStep()).isEqualTo(ConversationStep.AWAITING_CONFIRMATION);
        assertThat(moved.selectedService()).isEqualTo(ServiceType.DECOR);
        assertThat(moved.context().paymentMethod()).isEqualTo("PIX");
        assertThat(moved.updatedAt()).isEqualTo(createdAt.plusSeconds(120));
    }

    @Test
    void shouldSelectServiceAndAdvanceConversation() {
        Instant createdAt = Instant.parse("2026-04-06T10:00:00Z");
        Conversation conversation = Conversation.start("+5583999999999", createdAt);

        Conversation updated = conversation.selectService(
            ServiceType.DECOR_PINTURA,
            ConversationStep.AWAITING_CONFIRMATION,
            createdAt.plusSeconds(120)
        );

        assertThat(updated.selectedService()).isEqualTo(ServiceType.DECOR_PINTURA);
        assertThat(updated.currentStep()).isEqualTo(ConversationStep.AWAITING_CONFIRMATION);
        assertThat(updated.updatedAt()).isEqualTo(createdAt.plusSeconds(120));
    }

    @Test
    void shouldPersistPaymentMethodInContextAndAdvanceConversation() {
        Instant createdAt = Instant.parse("2026-04-06T10:00:00Z");
        Conversation conversation = new Conversation(
            "conv-1",
            "+5583999999999",
            ConversationStatus.ACTIVE,
            ConversationStep.AWAITING_PAYMENT_METHOD,
            ServiceType.DECOR,
            ConversationContext.empty(),
            createdAt,
            createdAt.plusSeconds(60),
            createdAt.plusSeconds(24 * 60 * 60)
        );

        Conversation updated = conversation.selectPaymentMethod(
            "PIX",
            ConversationStep.PAYMENT_LINK_SENT,
            createdAt.plusSeconds(180)
        );

        assertThat(updated.currentStep()).isEqualTo(ConversationStep.PAYMENT_LINK_SENT);
        assertThat(updated.selectedService()).isEqualTo(ServiceType.DECOR);
        assertThat(updated.context().paymentMethod()).isEqualTo("PIX");
        assertThat(updated.updatedAt()).isEqualTo(createdAt.plusSeconds(180));
    }
}
