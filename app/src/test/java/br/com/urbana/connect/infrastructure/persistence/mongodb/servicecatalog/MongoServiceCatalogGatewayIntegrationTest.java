package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Test
    void shouldSeedInitialCatalogOnStartup() {
        var catalog = serviceCatalogGateway.findAll();

        assertThat(catalog)
            .hasSize(4)
            .extracting(service -> service.type())
            .containsExactlyInAnyOrder(
                ServiceType.DECOR,
                ServiceType.DECOR_PINTURA,
                ServiceType.DECOR_FACHADA,
                ServiceType.DECOR_REFORMA
            );

        assertThat(serviceCatalogGateway.findByType(ServiceType.DECOR_REFORMA))
            .isPresent()
            .get()
            .extracting(service -> service.available(), service -> service.paymentLink())
            .containsExactly(false, null);
    }

    @Test
    void shouldReturnOnlyAvailableServices() {
        var availableServices = serviceCatalogGateway.findAvailable();

        assertThat(availableServices)
            .extracting(service -> service.type())
            .containsExactlyInAnyOrder(
                ServiceType.DECOR,
                ServiceType.DECOR_PINTURA,
                ServiceType.DECOR_FACHADA
            );
    }
}
