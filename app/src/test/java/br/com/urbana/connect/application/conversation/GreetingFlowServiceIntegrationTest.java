package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversation.ConversationDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class GreetingFlowServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl("urbana-connect"));
    }

    @Autowired
    private GreetingFlowService greetingFlowService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private WhatsAppMessageGateway whatsAppMessageGateway;

    @Test
    void shouldStartConversationInGreetingAndSendGreetingMessage() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583999999999";

        var conversation = greetingFlowService.handleIncomingMessage(phoneNumber, now);

        assertThat(conversation.currentStep()).isEqualTo(ConversationStep.GREETING);
        assertThat(countByPhoneNumber(phoneNumber)).isEqualTo(1);
        verify(whatsAppMessageGateway).sendGreeting(phoneNumber);
    }

    @Test
    void shouldRepeatGreetingWhenConversationIsStillInGreetingWithinWindow() {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583888888888";

        greetingFlowService.handleIncomingMessage(phoneNumber, now);
        var resumed = greetingFlowService.handleIncomingMessage(phoneNumber, now.plusSeconds(60));

        assertThat(resumed.currentStep()).isEqualTo(ConversationStep.GREETING);
        assertThat(countByPhoneNumber(phoneNumber)).isEqualTo(1);
        verify(whatsAppMessageGateway, times(2)).sendGreeting(phoneNumber);
    }

    @Test
    void shouldLogErrorAndNotThrowWhenGreetingSendFails(CapturedOutput output) {
        Instant now = Instant.parse("2026-04-05T09:00:00Z");
        String phoneNumber = "+5583777777777";

        doThrow(new IllegalStateException("token invalido"))
            .when(whatsAppMessageGateway)
            .sendGreeting(phoneNumber);

        assertThatCode(() -> greetingFlowService.handleIncomingMessage(phoneNumber, now))
            .doesNotThrowAnyException();

        assertThat(output)
            .contains("Falha ao enviar saudacao para +5583777777777 na etapa GREETING: token invalido");
    }

    private long countByPhoneNumber(String phoneNumber) {
        return mongoTemplate.count(
            Query.query(Criteria.where("phoneNumber").is(phoneNumber)),
            ConversationDocument.class
        );
    }
}
