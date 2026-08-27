package br.com.urbana.connect.domain.servicecatalog.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTypeTest {

    @Test
    void exposesCanonicalTypesAndMapsOnlyTheLegacyAlias() {
        assertThat(ServiceType.canonicalValues())
                .containsExactly(ServiceType.DECOR_INTERIORES, ServiceType.DECOR_PINTURA,
                        ServiceType.DECOR_FACHADA, ServiceType.DECOR_REFORMA);
        assertThat(ServiceType.DECOR.isCanonical()).isFalse();
        assertThat(ServiceType.DECOR_INTERIORES.isCanonical()).isTrue();
        assertThat(ServiceType.canonicalize(null)).isNull();
        assertThat(ServiceType.canonicalize(ServiceType.DECOR)).isEqualTo(ServiceType.DECOR_INTERIORES);
        assertThat(ServiceType.canonicalize(ServiceType.DECOR_REFORMA)).isEqualTo(ServiceType.DECOR_REFORMA);
    }
}
