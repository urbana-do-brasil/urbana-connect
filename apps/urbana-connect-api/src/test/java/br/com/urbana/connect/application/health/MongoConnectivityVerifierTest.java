package br.com.urbana.connect.application.health;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

class MongoConnectivityVerifierTest {

    @Test
    void shouldNotReportAvailableWhenMongoDoesNotAdvertiseAReplicaSet() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        given(mongoTemplate.executeCommand(any(Document.class)))
                .willReturn(new Document("ok", 1).append("isWritablePrimary", true));

        MongoConnectivityVerifier verifier = new MongoConnectivityVerifier(mongoTemplate);

        assertThat(verifier.isAvailable()).isFalse();
        verify(mongoTemplate).executeCommand(argThat((Document command) -> command.containsKey("hello")));
    }

    @Test
    void shouldReportAvailableWhenMongoAdvertisesAWritableReplicaSetPrimary() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        given(mongoTemplate.executeCommand(any(Document.class)))
                .willReturn(new Document("ok", 1)
                        .append("setName", "rs0")
                        .append("isWritablePrimary", true));

        MongoConnectivityVerifier verifier = new MongoConnectivityVerifier(mongoTemplate);

        assertThat(verifier.isAvailable()).isTrue();
        verify(mongoTemplate).executeCommand(argThat((Document command) -> command.containsKey("hello")));
    }
}
