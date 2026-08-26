package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MongoServiceCatalogGatewayIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl("urbana-connect"));
    }

    @Autowired
    private ServiceCatalogGateway serviceCatalogGateway;

    @Autowired
    private SpringDataServiceCatalogRepository repository;

    @Autowired
    private ServiceCatalogSeeder serviceCatalogSeeder;

    @Test
    void shouldSeedInitialCanonicalCatalogOnStartup() {
        var catalog = serviceCatalogGateway.findAll();

        assertThat(catalog)
            .hasSize(4)
            .extracting(ServiceCatalogItem::type)
            .containsExactlyInAnyOrder(
                ServiceType.DECOR_INTERIORES,
                ServiceType.DECOR_PINTURA,
                ServiceType.DECOR_FACHADA,
                ServiceType.DECOR_REFORMA
            );

        assertThat(catalog).allSatisfy(item -> {
            assertThat(item.available()).isTrue();
            assertThat(item.termsResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.paymentResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.briefingResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.areaRule()).isNotNull();
            assertThat(item.scope()).isNotBlank();
            assertThat(item.deliverables()).contains(
                "Manual PDF", "Tour Virtual", "3 opções de solução", "2 rodadas consolidadas");
            assertThat(item.process()).anyMatch(value -> value.contains("briefing"));
            assertThat(item.process()).anyMatch(value -> value.contains("Google Meet"));
            assertThat(item.process()).anyMatch(value -> value.contains("7 dias úteis"));
            assertThat(item.process()).anyMatch(value -> value.contains("e-mail"));
            assertThat(item.responsibilities()).isNotEmpty();
            assertThat(item.exclusions()).isNotEmpty();
            assertThat(item.support()).contains("3 meses");
        });

        assertThat(serviceCatalogGateway.findByType(ServiceType.DECOR_REFORMA))
            .isPresent()
            .get()
            .extracting(ServiceCatalogItem::available, ServiceCatalogItem::price)
            .containsExactly(true, new java.math.BigDecimal("450.00"));
    }

    @Test
    void shouldReturnOnlyAvailableCanonicalServices() {
        var availableServices = serviceCatalogGateway.findAvailable();

        assertThat(availableServices)
            .hasSize(4)
            .extracting(ServiceCatalogItem::type)
            .containsExactlyInAnyOrder(
                ServiceType.DECOR_INTERIORES,
                ServiceType.DECOR_PINTURA,
                ServiceType.DECOR_FACHADA,
                ServiceType.DECOR_REFORMA
            );
    }

    @Test
    void shouldCanonicalizeLegacyAliasAtTheGatewayBoundary() {
        ServiceCatalogItem resolved = serviceCatalogGateway.findByType(ServiceType.DECOR)
            .orElseThrow();

        assertThat(resolved.type()).isEqualTo(ServiceType.DECOR_INTERIORES);
        assertThat(resolved.name()).isEqualTo("Decor Interiores");
        assertThat(serviceCatalogGateway.findAll())
            .noneMatch(item -> item.type() == ServiceType.DECOR);
    }

    @Test
    void shouldRefreshCatalogCopyWithAllCanonicalRichFields() {
        ServiceCatalogDocument original = repository.findByType(ServiceType.DECOR_INTERIORES)
            .orElseThrow();
        ServiceCatalogDocument mutated = new ServiceCatalogDocument();
        mutated.setId(original.getId());
        mutated.setType(original.getType());
        mutated.setName("cópia antiga");
        mutated.setEmoji("❌");
        mutated.setPrice(new java.math.BigDecimal("1.00"));
        mutated.setScenarioText("copy antiga");
        mutated.setPresentationText("apresentação antiga");
        mutated.setTermsResource("https://mpago.la/legacy-terms");
        mutated.setPaymentResource("https://mpago.la/legacy-payment");
        mutated.setBriefingResource("https://forms.gle/legacy-briefing");
        mutated.setAreaRule(null);
        mutated.setScope(null);
        mutated.setDeliverables(null);
        mutated.setProcess(null);
        mutated.setResponsibilities(null);
        mutated.setExclusions(null);
        mutated.setSupport(null);
        mutated.setAvailable(false);
        repository.save(mutated);

        try {
            serviceCatalogSeeder.run(new DefaultApplicationArguments(new String[0]));

            ServiceCatalogItem updated = serviceCatalogGateway.findByType(ServiceType.DECOR_INTERIORES)
                .orElseThrow();

            ServiceCatalogItem expected = ServiceCatalogItem.canonicalCatalog().stream()
                .filter(item -> item.type() == ServiceType.DECOR_INTERIORES)
                .findFirst()
                .orElseThrow();

            assertThat(updated).isEqualTo(expected);
            assertThat(updated.termsResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(updated.paymentResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(updated.briefingResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(updated.paymentResource()).doesNotContain("mpago.la");
            assertThat(updated.briefingResource()).doesNotContain("forms.gle");
        } finally {
            repository.save(original);
        }
    }
}
