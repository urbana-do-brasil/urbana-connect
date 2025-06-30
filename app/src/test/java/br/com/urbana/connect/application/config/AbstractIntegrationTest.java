package br.com.urbana.connect.application.config;

import br.com.urbana.connect.config.WireMockConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static br.com.urbana.connect.config.WireMockConfig.WIREMOCK_PORT;

/**
 * Classe base abstrata para testes de integração.
 * Configura o container MongoDB do TestContainers e o WireMock uma única vez para todos os testes.
 * Todos os testes de integração devem estender esta classe.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = {WireMockConfig.class})
public abstract class AbstractIntegrationTest {

    /**
     * Container MongoDB estático compartilhado entre todos os testes
     */
    @Container
    protected static final MongoDBContainer mongoDBContainer = new MongoDBContainer(
            DockerImageName.parse("mongo:6.0")
    ).withReuse(true);

    static {
        mongoDBContainer.start();
    }

    /**
     * Configura dinamicamente as propriedades para usar as URIs do MongoDB e do WireMock.
     */
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry, WireMockServer wireMockServer) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("whatsapp.api-url", () -> "http://localhost:" + WIREMOCK_PORT);
    }
} 