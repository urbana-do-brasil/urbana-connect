package br.com.urbana.connect.infrastructure.persistence;

import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.enums.ConversationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Interface Spring Data MongoDB para Conversation.
 */
@Repository
public interface ConversationMongoRepository extends MongoRepository<Conversation, String> {
    
    List<Conversation> findByCustomerIdOrderByStartTimeDesc(String customerId);
    
    List<Conversation> findByCustomerIdAndStatus(String customerId, ConversationStatus status);
} 