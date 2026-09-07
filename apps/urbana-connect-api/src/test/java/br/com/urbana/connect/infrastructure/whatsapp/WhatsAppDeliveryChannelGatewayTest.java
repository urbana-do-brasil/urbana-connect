package br.com.urbana.connect.infrastructure.whatsapp;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDispatchException;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WhatsAppDeliveryChannelGatewayTest {
    @Test
    void delegatesThePayloadAndPreservesProviderId() {
        WhatsAppMessageGateway whatsapp = mock(WhatsAppMessageGateway.class);
        when(whatsapp.sendTextMessageWithResult("5511999999999", "payload"))
                .thenReturn("wamid-1");
        WhatsAppDeliveryChannelGateway gateway = new WhatsAppDeliveryChannelGateway(whatsapp);

        var result = gateway.send(intent(DeliveryOutboxKind.WHATSAPP_MENU), "5511999999999");

        assertThat(result.providerMessageId()).isEqualTo("wamid-1");
    }

    @Test
    void rejectsAnEmailIntentBeforeCallingWhatsApp() {
        WhatsAppMessageGateway whatsapp = mock(WhatsAppMessageGateway.class);
        WhatsAppDeliveryChannelGateway gateway = new WhatsAppDeliveryChannelGateway(whatsapp);

        assertThatThrownBy(() -> gateway.send(intent(DeliveryOutboxKind.EMAIL_HUMAN_HANDOFF), "dest"))
                .isInstanceOf(DeliveryDispatchException.class)
                .hasMessageContaining("non-WhatsApp");
    }

    private static DeliveryOutbox intent(DeliveryOutboxKind kind) {
        Instant now = Instant.parse("2026-09-03T12:00:00Z");
        return DeliveryOutbox.pending("event-1", kind, "opaque", "payload", "correlation-1", now);
    }
}
