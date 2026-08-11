package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.List;

public interface SpringDataReceptionTurnRepository extends MongoRepository<ReceptionTurnDocument, String> {
    Optional<ReceptionTurnDocument> findByInboundMessageIdsContains(String messageId);

    Optional<ReceptionTurnDocument> findFirstByContactIdOrderByAcceptedAtDesc(String contactId);
}
