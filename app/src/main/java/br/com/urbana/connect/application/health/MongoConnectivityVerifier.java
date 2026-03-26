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
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            Number status = result.get("ok", Number.class);
            return status != null && status.doubleValue() == 1.0d;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
