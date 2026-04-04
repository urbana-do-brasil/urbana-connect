package br.com.urbana.connect.domain.servicecatalog.model;

import java.math.BigDecimal;

public record ServiceCatalogItem(
        ServiceType type,
        String name,
        String emoji,
        String scenarioText,
        String presentationText,
        BigDecimal price,
        String paymentLink,
        String briefingLink,
        boolean available) {
}
