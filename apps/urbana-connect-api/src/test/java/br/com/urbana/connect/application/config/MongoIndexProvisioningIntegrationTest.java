package br.com.urbana.connect.application.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Testcontainers
class MongoIndexProvisioningIntegrationTest {

    @Container
    static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.5");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.getReplicaSetUrl("pee104-indexes"));
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void provisionsReceptionMessageIdentityIndexesWhenAutoCreationIsDisabled() {
        List<Document> indexes = mongoTemplate.getCollection("reception_messages")
                .listIndexes()
                .into(new ArrayList<>());

        Document providerMessageIdIndex = indexWithKey(indexes, "providerMessageId");
        assertThat(providerMessageIdIndex).isNotNull();
        assertThat(providerMessageIdIndex.getBoolean("unique", false)).isTrue();
        assertThat(providerMessageIdIndex.getBoolean("sparse", false)).isTrue();

        Document eventIdIndex = indexWithKey(indexes, "eventId");
        assertThat(eventIdIndex).isNotNull();
        assertThat(eventIdIndex.getBoolean("unique", false)).isTrue();
    }

    private static Document indexWithKey(List<Document> indexes, String field) {
        return indexes.stream()
                .filter(index -> new Document(field, 1).equals(index.get("key")))
                .findFirst()
                .orElse(null);
    }
}
