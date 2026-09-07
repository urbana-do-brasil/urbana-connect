package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase;

import java.util.Objects;

/** Failure carrying the adapter's last known provider-dispatch phase. */
public final class DeliveryDispatchException extends RuntimeException {
    private final DeliveryDispatchPhase phase;

    public DeliveryDispatchException(String message, DeliveryDispatchPhase phase) {
        super(message);
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public DeliveryDispatchException(String message, DeliveryDispatchPhase phase, Throwable cause) {
        super(message, cause);
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public DeliveryDispatchPhase phase() {
        return phase;
    }
}
