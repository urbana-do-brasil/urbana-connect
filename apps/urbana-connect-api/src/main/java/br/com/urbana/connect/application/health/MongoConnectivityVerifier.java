package br.com.urbana.connect.application.health;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoConnectivityVerifier {

    private final MongoTemplate mongoTemplate;

    public MongoConnectivityVerifier(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public boolean isAvailable() {
        try {
            Document result = mongoTemplate.executeCommand(new Document("hello", 1));
            Number status = result.get("ok", Number.class);
            String replicaSetName = result.getString("setName");
            Boolean writablePrimary = result.getBoolean("isWritablePrimary");
            return status != null
                    && status.doubleValue() == 1.0d
                    && replicaSetName != null
                    && !replicaSetName.isBlank()
                    && Boolean.TRUE.equals(writablePrimary);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
