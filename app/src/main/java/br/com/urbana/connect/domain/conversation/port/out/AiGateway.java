package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.conversation.model.AiContext;
import br.com.urbana.connect.domain.conversation.model.AiInterpretation;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;

public interface AiGateway {

    AiInterpretation interpret(AiContext context);

    ConversationalAiReply converse(AiContext context);
}
