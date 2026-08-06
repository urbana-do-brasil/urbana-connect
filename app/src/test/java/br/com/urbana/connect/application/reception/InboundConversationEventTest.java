package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InboundConversationEventTest {
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
