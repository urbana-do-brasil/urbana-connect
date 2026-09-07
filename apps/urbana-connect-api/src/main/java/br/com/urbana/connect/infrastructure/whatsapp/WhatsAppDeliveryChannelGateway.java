package br.com.urbana.connect.infrastructure.whatsapp;

import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.port.out.DeliveryChannelGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDispatchException;

import java.util.Objects;

/**
 * Adapts the existing WhatsApp Cloud boundary to the durable outbox. The
 * adapter intentionally accepts only WhatsApp intents; e-mail has its own
 * channel and cannot accidentally be sent through this provider.
 */
public final class WhatsAppDeliveryChannelGateway implements DeliveryChannelGateway {
    private final WhatsAppMessageGateway whatsapp;

    public WhatsAppDeliveryChannelGateway(WhatsAppMessageGateway whatsapp) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp");
    }

    @Override
    public DeliveryResult send(DeliveryOutbox outbox, String resolvedDestination) {
        if (outbox == null || outbox.kind() == DeliveryOutboxKind.EMAIL_HUMAN_HANDOFF) {
            throw new DeliveryDispatchException("WhatsApp adapter received a non-WhatsApp intent",
                    br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase.PRE_DISPATCH);
        }
        if (resolvedDestination == null || resolvedDestination.isBlank()) {
            throw new DeliveryDispatchException("WhatsApp destination is blank",
                    br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase.PRE_DISPATCH);
        }
        try {
            return new DeliveryResult(whatsapp.sendTextMessageWithResult(
                    resolvedDestination, outbox.payload()));
        } catch (DeliveryDispatchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DeliveryDispatchException("WhatsApp adapter failed without a known phase",
                    br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN,
                    exception);
        }
    }
}
