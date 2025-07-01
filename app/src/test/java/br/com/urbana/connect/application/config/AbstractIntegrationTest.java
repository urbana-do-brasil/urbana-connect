package br.com.urbana.connect.application.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Classe base abstrata para testes de integração.
 * Configura o container MongoDB do TestContainers uma única vez para todos os testes.
 * Todos os testes de integração devem estender esta classe para reutilizar o container.
 */
@Testcontainers
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
     * Configura dinamicamente a propriedade spring.data.mongodb.uri
     * para usar a URI do container MongoDB.
     */
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }
} 