package br.com.urbana.connect.infrastructure.persistence.mongodb.conversationmessage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringDataConversationMessageRepository extends MongoRepository<ConversationMessageDocument, String> {

    List<ConversationMessageDocument> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);
}
