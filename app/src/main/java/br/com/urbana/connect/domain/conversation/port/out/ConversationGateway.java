package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.conversation.model.Conversation;

import java.util.Optional;

public interface ConversationGateway {

    Conversation save(Conversation conversation);

    Optional<Conversation> findLatestByPhoneNumber(String phoneNumber);
}
