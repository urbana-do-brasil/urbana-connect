package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataReceptionMessageRepository extends MongoRepository<ReceptionMessageDocument, String> {
    Optional<ReceptionMessageDocument> findByEventId(String eventId);
    List<ReceptionMessageDocument> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}
