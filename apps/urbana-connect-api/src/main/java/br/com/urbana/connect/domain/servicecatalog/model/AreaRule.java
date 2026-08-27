package br.com.urbana.connect.domain.servicecatalog.model;

import java.math.BigDecimal;

public enum AreaRule {
    UP_TO_20_SQM_PER_ENVIRONMENT(new BigDecimal("20.00"), "Até 20 m² por ambiente"),
    UNLIMITED_BY_CATALOG(null, "Sem limite de m²");

    private final BigDecimal maximumSquareMeters;
    private final String description;

    AreaRule(BigDecimal maximumSquareMeters, String description) {
        this.maximumSquareMeters = maximumSquareMeters;
        this.description = description;
    }

    public BigDecimal maximumSquareMeters() {
        return maximumSquareMeters;
    }

    public String description() {
        return description;
    }

    public boolean hasLimit() {
        return maximumSquareMeters != null;
    }

    public boolean accepts(BigDecimal squareMeters) {
        if (squareMeters == null || squareMeters.signum() < 0) {
            return false;
        }
        return !hasLimit() || squareMeters.compareTo(maximumSquareMeters) <= 0;
    }

    public boolean requiresArchitectReview(BigDecimal squareMeters) {
        return hasLimit()
                && squareMeters != null
                && squareMeters.compareTo(maximumSquareMeters) > 0;
    }
}
