package br.com.urbana.connect;

import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationContentGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversationcontent.ConversationContentSeeder;
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
    private ConversationMessageGateway conversationMessageGateway;

    @MockitoBean
    private ConversationContentGateway conversationContentGateway;

    @MockitoBean
    private WhatsAppMessageGateway whatsAppMessageGateway;

    @MockitoBean
    private ServiceCatalogSeeder serviceCatalogSeeder;

    @MockitoBean
    private ConversationContentSeeder conversationContentSeeder;

    @Test
    void contextLoads() {
    }
}
