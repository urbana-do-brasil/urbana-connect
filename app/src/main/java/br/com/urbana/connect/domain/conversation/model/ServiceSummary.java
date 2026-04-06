package br.com.urbana.connect.domain.conversation.model;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;

public record ServiceSummary(
        ServiceType type,
        String name,
        String scenarioText) {
}
