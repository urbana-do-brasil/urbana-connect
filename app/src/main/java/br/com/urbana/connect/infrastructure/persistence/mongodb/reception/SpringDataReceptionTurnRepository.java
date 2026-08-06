package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SpringDataReceptionTurnRepository extends MongoRepository<ReceptionTurnDocument, String> {
    Optional<ReceptionTurnDocument> findByInboundMessageIdsContains(String messageId);
}
