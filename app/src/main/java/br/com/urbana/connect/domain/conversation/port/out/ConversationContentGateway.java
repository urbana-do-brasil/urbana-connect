package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.conversation.model.ConversationContentKey;

import java.util.Optional;

public interface ConversationContentGateway {

    Optional<String> findActiveValue(ConversationContentKey key);
}
