package br.com.urbana.connect.infrastructure.persistence.mongodb.conversation;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SpringDataConversationRepository extends MongoRepository<ConversationDocument, String> {

    Optional<ConversationDocument> findFirstByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}
