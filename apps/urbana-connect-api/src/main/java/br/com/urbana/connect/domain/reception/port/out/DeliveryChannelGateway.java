package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;

/**
 * Outbound channel boundary used by the durable delivery worker. Implementors
 * must throw {@link DeliveryDispatchException} with the last known dispatch
 * phase; the worker then applies the retry/reconciliation policy.
 */
public interface DeliveryChannelGateway {
    DeliveryResult send(DeliveryOutbox outbox, String resolvedDestination);

    record DeliveryResult(String providerMessageId) {
        public DeliveryResult {
            if (providerMessageId != null && providerMessageId.isBlank()) {
                providerMessageId = null;
            }
        }
    }
}
