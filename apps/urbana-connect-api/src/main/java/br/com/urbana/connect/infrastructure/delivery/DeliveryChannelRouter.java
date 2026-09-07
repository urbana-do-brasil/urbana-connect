package br.com.urbana.connect.infrastructure.delivery;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.port.out.DeliveryChannelGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDispatchException;
import br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Routes each durable intent to its explicitly configured channel adapter. */
public final class DeliveryChannelRouter implements DeliveryChannelGateway {
    private final Map<DeliveryOutboxKind, DeliveryChannelGateway> channels;

    public DeliveryChannelRouter(Map<DeliveryOutboxKind, DeliveryChannelGateway> channels) {
        Objects.requireNonNull(channels, "channels");
        EnumMap<DeliveryOutboxKind, DeliveryChannelGateway> copy = new EnumMap<>(DeliveryOutboxKind.class);
        channels.forEach((kind, channel) -> copy.put(Objects.requireNonNull(kind, "kind"),
                Objects.requireNonNull(channel, "channel")));
        this.channels = Map.copyOf(copy);
    }

    @Override
    public DeliveryResult send(DeliveryOutbox outbox, String resolvedDestination) {
        if (outbox == null) {
            throw new DeliveryDispatchException("delivery intent is required", DeliveryDispatchPhase.PRE_DISPATCH);
        }
        DeliveryChannelGateway channel = channels.get(outbox.kind());
        if (channel == null) {
            throw new DeliveryDispatchException("no channel is configured for " + outbox.kind(),
                    DeliveryDispatchPhase.PRE_DISPATCH);
        }
        return channel.send(outbox, resolvedDestination);
    }
}
