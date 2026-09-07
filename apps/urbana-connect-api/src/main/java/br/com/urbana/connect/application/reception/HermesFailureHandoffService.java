package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;

import java.time.Clock;
import java.util.Objects;

/**
 * Records human-ownership work before a best-effort customer acknowledgement.
 * The worker that delivers EMAIL_HUMAN_HANDOFF is intentionally separate from
 * the webhook transaction and must not claim delivery before a provider reply.
 */
public final class HermesFailureHandoffService {
    static final String SUPPORT_MAILBOX_DESTINATION = "support-mailbox";

    private final DeliveryOutboxGateway outbox;
    private final ReceptionConversationGateway conversations;
    private final Clock clock;

    public HermesFailureHandoffService(DeliveryOutboxGateway outbox, Clock clock) {
        this(outbox, null, clock);
    }

    public HermesFailureHandoffService(DeliveryOutboxGateway outbox,
                                       ReceptionConversationGateway conversations,
                                       Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.conversations = conversations;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void record(InboundConversationEvent event, String correlationId, String reason) {
        Objects.requireNonNull(event, "event");
        String normalizedReason = required(reason, "reason");
        String normalizedCorrelation = required(correlationId, "correlationId");
        outbox.saveIfAbsent(DeliveryOutbox.pending(
                "hermes-failure:" + event.eventId() + ":human-handoff",
                DeliveryOutboxKind.EMAIL_HUMAN_HANDOFF,
                SUPPORT_MAILBOX_DESTINATION,
                payload(event, normalizedCorrelation, normalizedReason),
                normalizedCorrelation,
                clock.instant()));
        markHumanOwnership(event.contactId(), normalizedReason);
        outbox.saveIfAbsent(DeliveryOutbox.pending(
                "hermes-failure:" + event.eventId() + ":handoff-ack",
                DeliveryOutboxKind.WHATSAPP_HUMAN_HANDOFF_ACK,
                "contact:" + event.contactId(),
                HermesWebhookMessageHandler.SAFE_HUMAN_HANDOFF_MESSAGE,
                normalizedCorrelation,
                clock.instant()));
    }

    private void markHumanOwnership(String contactId, String reason) {
        if (conversations == null) {
            return;
        }
        ReceptionConversation current = conversations.findByContactId(contactId)
                .orElseThrow(() -> new IllegalStateException("conversation is required before human handoff"));
        if (current.isHuman()) return;
        ReceptionConversation human = current.requestHumanHandoff(reason, clock.instant());
        conversations.saveExpected(human, current.version());
    }

    private static String payload(InboundConversationEvent event, String correlationId, String reason) {
        // Never include the raw WhatsApp number or inbound message body in an outbox payload.
        return "reason=" + reason
                + "; eventId=" + event.eventId()
                + "; contactRef=" + event.contactId()
                + "; correlationId=" + correlationId;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
