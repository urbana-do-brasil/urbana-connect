package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;

import java.time.Clock;
import java.util.Objects;

/** Writes Hermes customer-facing messages as idempotent outbox intents. */
public final class HermesOutboundPublisher {
    private final DeliveryOutboxGateway outbox;
    private final Clock clock;

    public HermesOutboundPublisher(DeliveryOutboxGateway outbox, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void reply(InboundConversationEvent event, String correlationId, AgentOutput output) {
        Objects.requireNonNull(output, "output");
        publish(event, correlationId, DeliveryOutboxKind.WHATSAPP_HERMES_REPLY, "reply", output.message());
    }

    public void reconciledReply(InboundConversationEvent event, String correlationId, String message) {
        publish(event, correlationId, DeliveryOutboxKind.WHATSAPP_HERMES_REPLY, "reply", message);
    }

    public void humanHandoffAcknowledgement(InboundConversationEvent event, String correlationId, String message) {
        publish(event, correlationId, DeliveryOutboxKind.WHATSAPP_HUMAN_HANDOFF_ACK, "handoff-ack", message);
    }

    private void publish(InboundConversationEvent event, String correlationId, DeliveryOutboxKind kind,
                         String suffix, String message) {
        Objects.requireNonNull(event, "event");
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        outbox.saveIfAbsent(DeliveryOutbox.pending(
                "hermes:" + event.eventId() + ":" + suffix,
                kind,
                "contact:" + event.contactId(),
                message,
                correlationId,
                clock.instant()));
    }
}
