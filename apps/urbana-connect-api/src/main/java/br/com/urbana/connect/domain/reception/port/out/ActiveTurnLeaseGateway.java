package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;

import java.time.Instant;
import java.util.Optional;
import java.time.Duration;

public interface ActiveTurnLeaseGateway {
    /** Must atomically reject a second RUNNING lease for the same session. */
    Optional<ActiveTurnLease> acquire(ActiveTurnLease requested);

    Optional<ActiveTurnLease> findRunning(String sessionId, Instant now);

    default Optional<ActiveTurnLease> findBlocking(String sessionId, Instant now) {
        return findRunning(sessionId, now);
    }

    ActiveTurnLease revoke(String sessionId, String turnId, Instant now);

    default ActiveTurnLease revoke(String sessionId, String turnId, String claimToken, Instant now) {
        return revoke(sessionId, turnId, now);
    }

    ActiveTurnLease expire(String sessionId, String turnId, Instant now);

    default ActiveTurnLease markReconciling(String sessionId, String turnId, String claimToken, Instant now) {
        throw new UnsupportedOperationException("reconciling leases are not supported");
    }

    default ActiveTurnLease heartbeat(String sessionId, String turnId, String claimToken,
                                     Instant now, Duration ttl) {
        throw new UnsupportedOperationException("lease heartbeat is not supported");
    }
}
