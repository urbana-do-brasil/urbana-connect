package br.com.urbana.connect.infrastructure.persistence;

import br.com.urbana.connect.domain.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Interface Spring Data MongoDB para Message.
 */
@Repository
public interface MessageMongoRepository extends MongoRepository<Message, String> {
    
    List<Message> findByConversationIdOrderByTimestampAsc(String conversationId);
    
    List<Message> findByCustomerIdOrderByTimestampDesc(String customerId);
    
    Optional<Message> findByWhatsappMessageId(String whatsappMessageId);
} 