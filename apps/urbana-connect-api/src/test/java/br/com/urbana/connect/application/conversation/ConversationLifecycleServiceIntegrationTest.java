package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationStatus;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.infrastructure.persistence.mongodb.conversation.ConversationDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ConversationLifecycleServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl("urbana-connect"));
    }

    @Autowired
    private ConversationLifecycleService conversationLifecycleService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void shouldStartNewConversationInGreeting() {
        Instant now = Instant.parse("2026-04-04T10:00:00Z");

        var conversation = conversationLifecycleService.resumeOrStart("+5583999999999", now);

        assertThat(conversation.id()).isNotBlank();
        assertThat(conversation.phoneNumber()).isEqualTo("+5583999999999");
        assertThat(conversation.status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(conversation.currentStep()).isEqualTo(ConversationStep.GREETING);
        assertThat(conversation.createdAt()).isEqualTo(now);
        assertThat(conversation.updatedAt()).isEqualTo(now);
        assertThat(conversation.expiresAt()).isEqualTo(now.plus(24, ChronoUnit.HOURS));
    }

    @Test
    void shouldResumeActiveConversationWithin24Hours() {
        String phoneNumber = "+5583888888888";
        Instant now = Instant.parse("2026-04-04T10:00:00Z");

        var first = conversationLifecycleService.resumeOrStart(phoneNumber, now);
        var resumed = conversationLifecycleService.resumeOrStart(phoneNumber, now.plus(23, ChronoUnit.HOURS));

        assertThat(resumed.id()).isEqualTo(first.id());
        assertThat(resumed.createdAt()).isEqualTo(first.createdAt());
        assertThat(resumed.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(countByPhoneNumber(phoneNumber)).isEqualTo(1);
    }

    @Test
    void shouldExpireConversationAndStartANewOneAfter24Hours() {
        String phoneNumber = "+5583777777777";
        Instant now = Instant.parse("2026-04-04T10:00:00Z");

        var first = conversationLifecycleService.resumeOrStart(phoneNumber, now);
        var restarted = conversationLifecycleService.resumeOrStart(phoneNumber, now.plus(25, ChronoUnit.HOURS));

        assertThat(restarted.id()).isNotEqualTo(first.id());
        assertThat(restarted.status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(restarted.currentStep()).isEqualTo(ConversationStep.GREETING);
        assertThat(restarted.createdAt()).isEqualTo(now.plus(25, ChronoUnit.HOURS));

        List<ConversationDocument> documents = mongoTemplate.find(
            Query.query(Criteria.where("phoneNumber").is(phoneNumber)),
            ConversationDocument.class
        );

        assertThat(documents).hasSize(2);
        assertThat(documents)
            .extracting(ConversationDocument::getStatus)
            .containsExactlyInAnyOrder(ConversationStatus.ACTIVE, ConversationStatus.EXPIRED);
    }

    private long countByPhoneNumber(String phoneNumber) {
        return mongoTemplate.count(
            Query.query(Criteria.where("phoneNumber").is(phoneNumber)),
            ConversationDocument.class
        );
    }
}
