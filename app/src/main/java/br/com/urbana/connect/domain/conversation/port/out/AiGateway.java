package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.conversation.model.AssembledContext;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;

public interface AiGateway {

    ConversationalAiReply converse(AssembledContext context);
}
