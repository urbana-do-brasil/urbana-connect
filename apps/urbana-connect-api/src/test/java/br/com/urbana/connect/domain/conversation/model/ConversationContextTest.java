package br.com.urbana.connect.domain.conversation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationContextTest {

    @Test
    void shouldReturnEmptyContextWithoutPaymentMethod() {
        assertThat(ConversationContext.empty().paymentMethod()).isNull();
    }

    @Test
    void shouldCreateNewContextWithPaymentMethod() {
        ConversationContext updated = ConversationContext.empty().withPaymentMethod("PIX");

        assertThat(updated.paymentMethod()).isEqualTo("PIX");
        assertThat(ConversationContext.empty().paymentMethod()).isNull();
    }
}
