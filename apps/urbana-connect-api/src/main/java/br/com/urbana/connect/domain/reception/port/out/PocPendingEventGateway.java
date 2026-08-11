package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.PocPendingEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Durable queue port for the local POC; Mongo is the production adapter. */
public interface PocPendingEventGateway {
    PocPendingEvent saveIfAbsent(PocPendingEvent event);

    default PocPendingEvent save(PocPendingEvent event) {
        return saveIfAbsent(event);
    }

    Optional<PocPendingEvent> findByEventId(String eventId);

    Optional<PocPendingEvent> claim(String eventId, String claimToken, Instant now, Duration leaseTtl);

    List<PocPendingEvent> findRecoverable(Instant now, Duration leaseTtl);

    /** Returns all records for one contact in the adapter's durable order. */
    default List<PocPendingEvent> findByContactId(String contactId) {
        return List.of();
    }

    Optional<PocPendingEvent> complete(String eventId, String claimToken, Instant now);

    /**
     * Returns a currently owned claim to QUEUED only after the caller has
     * established that no remote execution was started or that retry is safe.
     * Implementations must bind the mutation to the current claim token.
     */
    default Optional<PocPendingEvent> requeueIfRetrySafe(String eventId, String claimToken, Instant now) {
        return Optional.empty();
    }
}
