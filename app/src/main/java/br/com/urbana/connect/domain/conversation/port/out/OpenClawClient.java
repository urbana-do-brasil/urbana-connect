package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.conversation.model.OpenClawTurnRequest;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnResult;

public interface OpenClawClient {

    OpenClawTurnResult sendTurn(OpenClawTurnRequest request);
}
