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
    void shouldSeedInitialCatalogOnStartup() {
        var catalog = serviceCatalogGateway.findAll();

        assertThat(catalog)
            .hasSize(4)
            .extracting(ServiceCatalogItem::type)
            .containsExactlyInAnyOrder(
                ServiceType.DECOR,
                ServiceType.DECOR_PINTURA,
                ServiceType.DECOR_FACHADA,
                ServiceType.DECOR_REFORMA
            );

        assertThat(serviceCatalogGateway.findByType(ServiceType.DECOR_REFORMA))
            .isPresent()
            .get()
            .extracting(ServiceCatalogItem::available, ServiceCatalogItem::paymentLink)
            .containsExactly(false, null);
    }

    @Test
    void shouldReturnOnlyAvailableServices() {
        var availableServices = serviceCatalogGateway.findAvailable();

        assertThat(availableServices)
            .extracting(ServiceCatalogItem::type)
            .containsExactlyInAnyOrder(
                ServiceType.DECOR,
                ServiceType.DECOR_PINTURA,
                ServiceType.DECOR_FACHADA
            );
    }

    @Test
    void shouldRefreshCatalogCopyWithoutOverwritingOperationalFields() throws Exception {
        ServiceCatalogDocument original = repository.findByType(ServiceType.DECOR)
            .orElseThrow();
        ServiceCatalogDocument mutated = new ServiceCatalogDocument();
        mutated.setId(original.getId());
        mutated.setType(original.getType());
        mutated.setName(original.getName());
        mutated.setEmoji(original.getEmoji());
        mutated.setPrice(original.getPrice());
        mutated.setScenarioText("copy antiga");
        mutated.setPresentationText("apresentação antiga");
        mutated.setPaymentLink("https://custom.example/pagamento");
        mutated.setBriefingLink("https://custom.example/briefing");
        mutated.setAvailable(false);
        repository.save(mutated);

        try {
            serviceCatalogSeeder.run(new DefaultApplicationArguments(new String[0]));

            ServiceCatalogItem updated = serviceCatalogGateway.findByType(ServiceType.DECOR)
                .orElseThrow();

            assertThat(updated.scenarioText()).isEqualTo("Quero renovar meu espaço interno sem gastar muito, nada de quebra-quebra.");
            assertThat(updated.presentationText())
                .contains("Para espaços de até 20m², temos a Decor 🛋️")
                .contains("solução faça você mesmo");
            assertThat(updated.paymentLink()).isEqualTo("https://custom.example/pagamento");
            assertThat(updated.briefingLink()).isEqualTo("https://custom.example/briefing");
            assertThat(updated.available()).isFalse();
        } finally {
            repository.save(original);
        }
    }
}
