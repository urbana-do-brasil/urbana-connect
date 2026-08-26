package br.com.urbana.connect.domain.reception.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerFactTypeTest {

    @Test
    void validatesAllowedTypesAndIdentifiesTheThreeIcpFields() {
        assertThat(CustomerFactType.isAllowed(null)).isFalse();
        assertThat(CustomerFactType.isAllowed("occupation")).isTrue();
        assertThat(CustomerFactType.isAllowed("unknown")).isFalse();

        assertThat(CustomerFactType.PRONOUN_PREFERENCE.isIcpField()).isTrue();
        assertThat(CustomerFactType.FIRST_TIME_HIRING.isIcpField()).isTrue();
        assertThat(CustomerFactType.OCCUPATION.isIcpField()).isTrue();
        assertThat(CustomerFactType.NEED.isIcpField()).isFalse();
        assertThat(CustomerFactType.SELECTED_SERVICE.isIcpField()).isFalse();

        assertThat(CustomerFactType.icpFieldNames())
                .containsExactly("PRONOUN_PREFERENCE", "FIRST_TIME_HIRING", "OCCUPATION");
        assertThat(CustomerFactType.isIcpField(null)).isFalse();
        assertThat(CustomerFactType.isIcpField("first_time_hiring")).isTrue();
        assertThat(CustomerFactType.isIcpField("NEED")).isFalse();
    }
}
