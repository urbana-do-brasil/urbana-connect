package br.com.urbana.connect.domain.conversation.model;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;

public record AiInterpretation(
        IntentType intent,
        ServiceType selectedService,
        String suggestedResponse) {

    public static AiInterpretation unknown() {
        return new AiInterpretation(IntentType.UNKNOWN, null, null);
    }
}
