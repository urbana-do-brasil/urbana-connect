package br.com.urbana.connect;

import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog.ServiceCatalogSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import br.com.urbana.connect.application.health.MongoConnectivityVerifier;

@SpringBootTest
@ActiveProfiles("test")
class UrbanaConnectApplicationTests {

    @MockitoBean
    private MongoConnectivityVerifier mongoConnectivityVerifier;

    @MockitoBean
    private ServiceCatalogGateway serviceCatalogGateway;

    @MockitoBean
    private ConversationGateway conversationGateway;

    @MockitoBean
    private ServiceCatalogSeeder serviceCatalogSeeder;

    @Test
    void contextLoads() {
    }
}
