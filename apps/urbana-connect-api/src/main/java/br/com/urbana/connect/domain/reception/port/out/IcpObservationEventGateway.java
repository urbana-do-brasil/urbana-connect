package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.IcpObservationEvent;

/** Durable, non-transcript sink for internal ICP continuation observations. */
public interface IcpObservationEventGateway {
    /** Returns false when the logical observation already exists. */
    boolean appendIfAbsent(IcpObservationEvent event);
}
