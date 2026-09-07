package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HermesFailureHandoffServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void recordsTheDurableIntentAndTransfersConversationOwnershipToHuman() {
        DeliveryOutboxGateway outbox = mock(DeliveryOutboxGateway.class);
        ReceptionConversationGateway conversations = mock(ReceptionConversationGateway.class);
        ReceptionConversation current = ReceptionConversation.start("conversation-1", "wa:contact-hash", NOW);
        when(conversations.findByContactId("wa:contact-hash")).thenReturn(Optional.of(current));
        HermesFailureHandoffService service = new HermesFailureHandoffService(outbox, conversations,
                Clock.fixed(NOW, ZoneOffset.UTC));
        InboundConversationEvent event = new InboundConversationEvent("event-1", "wa:contact-hash",
                ReceptionMessageType.TEXT, "oi", NOW);

        service.record(event, "correlation-1", "HERMES_FAILED");

        verify(outbox, times(2)).saveIfAbsent(any(DeliveryOutbox.class));
        verify(conversations).saveExpected(any(ReceptionConversation.class), eq(0L));
        org.mockito.ArgumentCaptor<ReceptionConversation> captor =
                org.mockito.ArgumentCaptor.forClass(ReceptionConversation.class);
        verify(conversations).saveExpected(captor.capture(), eq(0L));
        assertThat(captor.getValue().isHuman()).isTrue();
        assertThat(captor.getValue().handoffReason()).isEqualTo("HERMES_FAILED");
    }
}
