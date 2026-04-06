package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.conversation.model.AiContext;
import br.com.urbana.connect.domain.conversation.model.AiInterpretation;

public interface AiGateway {

    AiInterpretation interpret(AiContext context);
}
