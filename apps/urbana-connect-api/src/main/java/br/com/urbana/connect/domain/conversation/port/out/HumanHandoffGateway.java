package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.conversation.model.HumanHandoffRequest;

public interface HumanHandoffGateway {

    void notifyTeam(HumanHandoffRequest request);
}
