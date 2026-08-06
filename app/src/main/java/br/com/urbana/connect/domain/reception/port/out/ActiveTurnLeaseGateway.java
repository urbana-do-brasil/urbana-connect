package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;

import java.time.Instant;
import java.util.Optional;

public interface ActiveTurnLeaseGateway {
    /** Must atomically reject a second RUNNING lease for the same session. */
    Optional<ActiveTurnLease> acquire(ActiveTurnLease requested);

    Optional<ActiveTurnLease> findRunning(String sessionId, Instant now);

    ActiveTurnLease revoke(String sessionId, String turnId, Instant now);

    ActiveTurnLease expire(String sessionId, String turnId, Instant now);
}
