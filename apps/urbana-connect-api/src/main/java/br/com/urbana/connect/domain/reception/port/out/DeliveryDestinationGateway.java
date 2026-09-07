package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;

import java.util.Optional;

/** Resolves an opaque outbox destination without exposing PII in the intent. */
public interface DeliveryDestinationGateway {
    Optional<String> resolve(DeliveryOutbox outbox);
}
