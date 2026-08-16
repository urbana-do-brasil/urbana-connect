package br.com.urbana.connect.infrastructure.persistence.mongodb;

import br.com.urbana.connect.domain.reception.model.CommercialStage;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.ReceptionConversationDocument;
import br.com.urbana.connect.infrastructure.persistence.mongodb.reception.ReceptionMessageDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class MongoTransactionBoundaryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl("pee104"));
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoTransactionManager mongoTransactionManager;

    private final List<String> conversationIds = new ArrayList<>();
    private final List<String> messageIds = new ArrayList<>();

    @AfterEach
    void cleanUpProbeDocuments() {
        if (!conversationIds.isEmpty()) {
            mongoTemplate.remove(Query.query(Criteria.where("_id").in(conversationIds)),
                    ReceptionConversationDocument.class);
        }
        if (!messageIds.isEmpty()) {
            mongoTemplate.remove(Query.query(Criteria.where("_id").in(messageIds)),
                    ReceptionMessageDocument.class);
        }
    }

    @Test
    void commitsConversationVersionAndTranscriptMessageAtomically() {
        String conversationId = probeId("conversation");
        String messageId = probeId("message");
        conversationIds.add(conversationId);
        messageIds.add(messageId);
        mongoTemplate.insert(conversation(conversationId));

        new org.springframework.transaction.support.TransactionTemplate(mongoTransactionManager)
                .executeWithoutResult(status -> {
                    mongoTemplate.updateFirst(
                            Query.query(Criteria.where("_id").is(conversationId)),
                            new Update().set("version", 1L).set("updatedAt", NOW.plusSeconds(1)),
                            ReceptionConversationDocument.class);
                    mongoTemplate.insert(message(messageId, conversationId));
                });

        ReceptionConversationDocument persistedConversation = mongoTemplate.findById(
                conversationId, ReceptionConversationDocument.class);
        ReceptionMessageDocument persistedMessage = mongoTemplate.findById(
                messageId, ReceptionMessageDocument.class);

        assertThat(persistedConversation).isNotNull();
        assertThat(persistedConversation.getVersion()).isEqualTo(1L);
        assertThat(persistedMessage).isNotNull();
        assertThat(persistedMessage.getConversationId()).isEqualTo(conversationId);
    }

    @Test
    void rollsBackConversationVersionAndTranscriptMessageWithoutResidue() {
        String conversationId = probeId("conversation");
        String messageId = probeId("message");
        conversationIds.add(conversationId);
        messageIds.add(messageId);
        mongoTemplate.insert(conversation(conversationId));

        assertThatThrownBy(() ->
                new org.springframework.transaction.support.TransactionTemplate(mongoTransactionManager)
                        .executeWithoutResult(status -> {
                            mongoTemplate.updateFirst(
                                    Query.query(Criteria.where("_id").is(conversationId)),
                                    new Update().set("version", 1L).set("updatedAt", NOW.plusSeconds(1)),
                                    ReceptionConversationDocument.class);
                            mongoTemplate.insert(message(messageId, conversationId));
                            throw new IllegalStateException("force PEE-104 rollback");
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("force PEE-104 rollback");

        ReceptionConversationDocument persistedConversation = mongoTemplate.findById(
                conversationId, ReceptionConversationDocument.class);
        ReceptionMessageDocument persistedMessage = mongoTemplate.findById(
                messageId, ReceptionMessageDocument.class);

        assertThat(persistedConversation).isNotNull();
        assertThat(persistedConversation.getVersion()).isZero();
        assertThat(persistedMessage).isNull();
        assertThat(mongoTemplate.exists(
                Query.query(Criteria.where("eventId").is("pee104-event-" + messageId)),
                ReceptionMessageDocument.class)).isFalse();
    }

    private static String probeId(String kind) {
        return "pee104-" + kind + "-" + UUID.randomUUID();
    }

    private static ReceptionConversationDocument conversation(String id) {
        ReceptionConversationDocument document = new ReceptionConversationDocument();
        document.setId(id);
        document.setContactId(id + "-contact");
        document.setMode(ReceptionMode.AI);
        document.setCommercialStage(CommercialStage.DISCOVERY);
        document.setTermsStatus(TermsStatus.NOT_PRESENTED);
        document.setPaymentStatus(PaymentStatus.NOT_STARTED);
        document.setCreatedAt(NOW);
        document.setUpdatedAt(NOW);
        document.setVersion(0);
        return document;
    }

    private static ReceptionMessageDocument message(String id, String conversationId) {
        ReceptionMessageDocument document = new ReceptionMessageDocument();
        document.setId(id);
        document.setEventId("pee104-event-" + id);
        document.setCorrelationId("pee104-correlation-" + id);
        document.setConversationId(conversationId);
        document.setContactId(conversationId + "-contact");
        document.setDirection(ReceptionMessageDirection.INBOUND);
        document.setSenderType(ReceptionMessageSender.CONTACT);
        document.setType(ReceptionMessageType.TEXT);
        document.setText("synthetic PEE-104 transaction probe");
        document.setCreatedAt(NOW);
        return document;
    }
}
