package br.com.urbana.connect.infrastructure.delivery;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.port.out.DeliveryChannelGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDispatchException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryChannelRouterTest {
    @Test
    void routesByIntentKind() {
        DeliveryChannelGateway whatsapp = mock(DeliveryChannelGateway.class);
        when(whatsapp.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("dest")))
                .thenReturn(new DeliveryChannelGateway.DeliveryResult("provider-1"));
        DeliveryChannelRouter router = new DeliveryChannelRouter(
                Map.of(DeliveryOutboxKind.WHATSAPP_MENU, whatsapp));

        var result = router.send(intent(DeliveryOutboxKind.WHATSAPP_MENU), "dest");

        assertThat(result.providerMessageId()).isEqualTo("provider-1");
    }

    @Test
    void keepsMissingChannelAsRetryableConfigurationFailure() {
        DeliveryChannelRouter router = new DeliveryChannelRouter(Map.of());

        assertThatThrownBy(() -> router.send(intent(DeliveryOutboxKind.EMAIL_HUMAN_HANDOFF), "dest"))
                .isInstanceOf(DeliveryDispatchException.class)
                .hasMessageContaining("no channel");
    }

    private static DeliveryOutbox intent(DeliveryOutboxKind kind) {
        return DeliveryOutbox.pending("event", kind, "opaque", "payload", "correlation",
                Instant.parse("2026-09-03T12:00:00Z"));
    }
}
