package br.com.urbana.connect.domain.servicecatalog.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AreaRuleTest {

    @Test
    void acceptsLimitedAndUnlimitedAreasAndRoutesOnlyOverflowToReview() {
        AreaRule limited = AreaRule.UP_TO_20_SQM_PER_ENVIRONMENT;
        AreaRule unlimited = AreaRule.UNLIMITED_BY_CATALOG;

        assertThat(limited.maximumSquareMeters()).isEqualByComparingTo("20.00");
        assertThat(limited.description()).isEqualTo("Até 20 m² por ambiente");
        assertThat(limited.hasLimit()).isTrue();
        assertThat(unlimited.maximumSquareMeters()).isNull();
        assertThat(unlimited.hasLimit()).isFalse();

        assertThat(limited.accepts(null)).isFalse();
        assertThat(limited.accepts(new BigDecimal("-0.01"))).isFalse();
        assertThat(limited.accepts(new BigDecimal("20.00"))).isTrue();
        assertThat(limited.accepts(new BigDecimal("20.01"))).isFalse();
        assertThat(unlimited.accepts(new BigDecimal("1000"))).isTrue();

        assertThat(limited.requiresArchitectReview(null)).isFalse();
        assertThat(limited.requiresArchitectReview(new BigDecimal("20"))).isFalse();
        assertThat(limited.requiresArchitectReview(new BigDecimal("20.01"))).isTrue();
        assertThat(unlimited.requiresArchitectReview(new BigDecimal("1000"))).isFalse();
    }
}
