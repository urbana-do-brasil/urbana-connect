package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.conversation.model.ConversationMessage;

import java.util.List;

public interface ConversationMessageGateway {

    ConversationMessage save(ConversationMessage message);

    List<ConversationMessage> findRecentByConversationId(String conversationId, int limit);
}
