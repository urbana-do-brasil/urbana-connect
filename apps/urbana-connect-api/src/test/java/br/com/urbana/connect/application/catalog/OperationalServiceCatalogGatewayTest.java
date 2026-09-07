package br.com.urbana.connect.application.catalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalServiceCatalogGatewayTest {

    @Test
    void requiresAllFourConfiguredResourcesWhenOperationalCatalogIsEnabled() {
        CatalogBaselineProperties properties = new CatalogBaselineProperties();
        properties.setEnabled(true);

        assertThatThrownBy(() -> new ConfiguredCatalogBaseline(properties))
                .hasMessageContaining("decor-interiores");
    }

    @Test
    void rejectsNonHttpsOperationalResourcesInsteadOfExposingThemToCustomers() {
        CatalogBaselineProperties properties = enabledProperties();
        properties.getServices().get("decor-pintura").setPaymentResource("http://insecure.example/checkout");

        assertThatThrownBy(() -> new ConfiguredCatalogBaseline(properties))
                .hasMessageContaining("HTTPS")
                .hasMessageContaining("decor-pintura");
    }

    @Test
    void doesNotOfferFixtureCatalogWhenTheEnvironmentDisablesFixtureFallback() {
        CatalogBaselineProperties properties = new CatalogBaselineProperties();
        properties.setFixtureFallback(false);

        ConfiguredCatalogBaseline baseline = new ConfiguredCatalogBaseline(properties);

        assertThat(baseline.all()).hasSize(4).allMatch(item -> !item.available());
    }

    @Test
    void usesMongoValueAsOverrideAndConfiguredBaselineWhenMongoIsUnavailable() {
        ConfiguredCatalogBaseline baseline = new ConfiguredCatalogBaseline(enabledProperties());
        ServiceCatalogGateway mongo = mock(ServiceCatalogGateway.class);
        ServiceCatalogItem mongoOverride = new ServiceCatalogItem(
                ServiceType.DECOR_PINTURA, "Decor Pintura especial", "🎨", "escopo", "apresentação",
                new BigDecimal("299.00"), "https://hml.example/terms/pintura-v2",
                "https://hml.example/payment/pintura-v2", "https://hml.example/briefing/pintura-v2",
                br.com.urbana.connect.domain.servicecatalog.model.AreaRule.UNLIMITED_BY_CATALOG,
                "escopo", List.of(), List.of(), List.of(), List.of(), "suporte", true);
        when(mongo.findAll()).thenReturn(List.of(mongoOverride));

        OperationalServiceCatalogGateway gateway = new OperationalServiceCatalogGateway(mongo, baseline);

        assertThat(gateway.findAll()).extracting(ServiceCatalogItem::name)
                .containsExactly("Decor Interiores", "Decor Pintura especial", "Decor Fachada", "Decor Reforma");

        when(mongo.findAll()).thenThrow(new IllegalStateException("Mongo unavailable"));
        assertThat(gateway.findAll()).extracting(ServiceCatalogItem::name)
                .containsExactly("Decor Interiores", "Decor Pintura", "Decor Fachada", "Decor Reforma");
    }

    private CatalogBaselineProperties enabledProperties() {
        CatalogBaselineProperties properties = new CatalogBaselineProperties();
        properties.setEnabled(true);
        for (String key : List.of("decor-interiores", "decor-pintura", "decor-fachada", "decor-reforma")) {
            CatalogBaselineProperties.ServiceProperties service = new CatalogBaselineProperties.ServiceProperties();
            service.setPrice(new BigDecimal("400.00"));
            service.setTermsResource("https://hml.example/terms/" + key);
            service.setPaymentResource("https://hml.example/payment/" + key);
            service.setBriefingResource("https://hml.example/briefing/" + key);
            service.setAvailable(true);
            properties.getServices().put(key, service);
        }
        return properties;
    }
}
