package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.ReceptionConversation;

import java.util.Optional;

public interface ReceptionConversationGateway {
    Optional<ReceptionConversation> findByContactId(String contactId);

    ReceptionConversation save(ReceptionConversation conversation);
}
