package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HermesOutboundPublisherTest {
    @Test
    void writesReplyAsAnOpaqueIdempotentOutboxIntent() {
        DeliveryOutboxGateway outbox = mock(DeliveryOutboxGateway.class);
        Instant now = Instant.parse("2026-09-03T12:00:00Z");
        HermesOutboundPublisher publisher = new HermesOutboundPublisher(outbox, Clock.fixed(now, ZoneOffset.UTC));
        InboundConversationEvent event = new InboundConversationEvent("wamid-1", "wa:opaque", ReceptionMessageType.TEXT,
                "entrada", now);

        publisher.reply(event, "corr-1", new AgentOutput("resposta Hermes", AgentNextAction.NONE));

        ArgumentCaptor<DeliveryOutbox> captor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(outbox).saveIfAbsent(captor.capture());
        assertThat(captor.getValue().eventKey()).isEqualTo("hermes:wamid-1:reply");
        assertThat(captor.getValue().kind()).isEqualTo(DeliveryOutboxKind.WHATSAPP_HERMES_REPLY);
        assertThat(captor.getValue().destinationRef()).isEqualTo("contact:wa:opaque");
    }
}
