package br.com.urbana.connect.domain.conversation.model;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;

import java.math.BigDecimal;

public record ServiceSummary(
        ServiceType type,
        String name,
        String scenarioText,
        BigDecimal price,
        boolean available) {
}
