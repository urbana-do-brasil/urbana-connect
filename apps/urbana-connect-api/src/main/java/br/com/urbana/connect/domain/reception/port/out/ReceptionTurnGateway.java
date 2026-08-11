package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.ReceptionTurn;

import java.util.Optional;

public interface ReceptionTurnGateway {
    ReceptionTurn save(ReceptionTurn turn);

    Optional<ReceptionTurn> findById(String turnId);

    Optional<ReceptionTurn> findByInboundMessageId(String messageId);

    default Optional<ReceptionTurn> findLatestByContactId(String contactId) {
        return Optional.empty();
    }
}
