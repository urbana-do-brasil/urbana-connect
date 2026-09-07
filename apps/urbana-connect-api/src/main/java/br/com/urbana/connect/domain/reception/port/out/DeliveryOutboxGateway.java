package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Persistence port for side effects emitted after a domain transaction. */
public interface DeliveryOutboxGateway {
    DeliveryOutbox saveIfAbsent(DeliveryOutbox outbox);

    Optional<DeliveryOutbox> findByEventKey(String eventKey);

    /** Entries due for delivery. Implementations backed by Mongo override it. */
    default List<DeliveryOutbox> findDue(Instant now, int limit) {
        return List.of();
    }

    /** Claims one entry atomically when the backing store supports it. */
    default Optional<DeliveryOutbox> claim(String eventKey, Instant now) {
        return findByEventKey(eventKey)
                .filter(value -> value.status() == br.com.urbana.connect.domain.reception.model.DeliveryOutboxStatus.PENDING
                        || value.status() == br.com.urbana.connect.domain.reception.model.DeliveryOutboxStatus.RETRYABLE)
                .map(value -> value.sending(now));
    }

    /**
     * Moves stale claims to an explicit ambiguous state after a process
     * restart.  A worker must never blindly resend an item whose provider call
     * may already have happened before the process stopped.
     */
    default int recoverStale(Instant now, Duration lease, int limit) {
        return 0;
    }

    /** Persists a state transition. In-memory adapters can use save-if-absent. */
    default DeliveryOutbox update(DeliveryOutbox outbox) {
        return saveIfAbsent(outbox);
    }
}
