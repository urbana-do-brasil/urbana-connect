package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SpringDataReceptionConversationRepository extends MongoRepository<ReceptionConversationDocument, String> {
    Optional<ReceptionConversationDocument> findByContactId(String contactId);
}
