package br.com.urbana.connect.infrastructure.persistence.mongodb.conversationmessage;

import br.com.urbana.connect.domain.conversation.model.ConversationMessage;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageType;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ExtendWith(SpringExtension.class)
class MongoConversationMessageGatewayIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl("urbana-connect"));
    }

    @Autowired
    private ConversationMessageGateway conversationMessageGateway;

    @Test
    void shouldPersistAndReadRecentMessagesInChronologicalOrder() {
        conversationMessageGateway.save(ConversationMessage.inbound(
            "conversation-1",
            "+5583999999999",
            ConversationMessageType.TEXT,
            "primeira",
            null,
            null,
            Instant.parse("2026-04-28T12:00:00Z"),
            "GREETING"
        ));
        conversationMessageGateway.save(ConversationMessage.outbound(
            "conversation-1",
            "+5583999999999",
            ConversationMessageType.TEXT,
            "segunda",
            Instant.parse("2026-04-28T12:01:00Z"),
            "GREETING"
        ));

        var messages = conversationMessageGateway.findRecentByConversationId("conversation-1", 5);

        assertThat(messages)
            .extracting(ConversationMessage::rawText)
            .containsExactly("primeira", "segunda");
    }
}
