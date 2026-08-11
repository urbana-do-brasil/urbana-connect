package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundConversationEventTest {
    @Test
    void preservesNonBlankTextWithoutTrimmingTheConversationalPayload() {
        String text = "  Oi  com  espaços consecutivos  \n  e margem  ";

        InboundConversationEvent event = new InboundConversationEvent(
                "text-1", "poc:ana", ReceptionMessageType.TEXT, text,
                Instant.parse("2026-08-05T12:00:00Z"));

        assertThat(event.text()).isEqualTo(text);
        assertThat(event.conversationalText()).isEqualTo(text);
    }

    @Test
    void rejectsAnAllWhitespaceOnlyTextEventWithoutAnotherPayload() {
        assertThatThrownBy(() -> new InboundConversationEvent(
                "blank-1", "poc:ana", ReceptionMessageType.TEXT, "  \n\t  ",
                Instant.parse("2026-08-05T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsMediaReferenceOutOfConversationalTextWhenThereIsNoUserText() {
        InboundConversationEvent event = new InboundConversationEvent(
                "proof-1", "poc:ana", ReceptionMessageType.PAYMENT_PROOF,
                null, null, "poc/payment-proof-fixture.svg", null,
                Instant.parse("2026-08-05T12:00:00Z"), null);

        assertThat(event.conversationalText()).isEmpty();
        assertThat(event.mediaFixture()).isEqualTo("poc/payment-proof-fixture.svg");
    }
}
