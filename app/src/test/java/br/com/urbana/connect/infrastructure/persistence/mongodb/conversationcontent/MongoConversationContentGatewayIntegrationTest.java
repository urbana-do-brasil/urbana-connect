package br.com.urbana.connect.infrastructure.persistence.mongodb.conversationcontent;

import br.com.urbana.connect.domain.conversation.model.ConversationContentKey;
import br.com.urbana.connect.domain.conversation.port.out.ConversationContentGateway;
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
class MongoConversationContentGatewayIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl("urbana-connect"));
    }

    @Autowired
    private ConversationContentGateway conversationContentGateway;

    @Autowired
    private SpringDataConversationContentRepository repository;

    @Test
    void shouldLoadSeededConversationContent() {
        assertThat(conversationContentGateway.findActiveValue(ConversationContentKey.GREETING_TEXT))
            .hasValueSatisfying(value -> assertThat(value).contains("Sou a Urba"));
    }

    @Test
    void shouldKeepExistingConversationContentValueWhenSeederRuns() throws Exception {
        ConversationContentDocument existing = repository.findByKey(ConversationContentKey.CLOSING_TEXT).orElseThrow();
        existing.setValue("copy customizada");
        existing.setUpdatedAt(Instant.parse("2026-04-28T12:00:00Z"));
        repository.save(existing);

        new ConversationContentSeeder(repository).run(null);

        assertThat(conversationContentGateway.findActiveValue(ConversationContentKey.CLOSING_TEXT))
            .hasValue("copy customizada");
    }
}
