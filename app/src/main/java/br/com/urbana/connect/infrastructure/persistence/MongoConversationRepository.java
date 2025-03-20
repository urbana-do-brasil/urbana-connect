package br.com.urbana.connect.infrastructure.persistence;

import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.enums.ConversationStatus;
import br.com.urbana.connect.domain.port.output.ConversationRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementação do repositório de conversas usando MongoDB.
 */
@Repository
public class MongoConversationRepository implements ConversationRepository {
    
    private final ConversationMongoRepository repository;
    
    public MongoConversationRepository(ConversationMongoRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public Conversation save(Conversation conversation) {
        return repository.save(conversation);
    }
    
    @Override
    public Optional<Conversation> findById(String id) {
        return repository.findById(id);
    }
    
    @Override
    public List<Conversation> findByCustomerId(String customerId) {
        return repository.findByCustomerIdOrderByStartTimeDesc(customerId);
    }
    
    @Override
    public List<Conversation> findByCustomerIdAndStatus(String customerId, ConversationStatus status) {
        return repository.findByCustomerIdAndStatus(customerId, status);
    }
    
    @Override
    public Conversation updateStatus(String id, ConversationStatus status) {
        Conversation conversation = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        conversation.setStatus(status);
        conversation.setLastActivityTime(LocalDateTime.now());
        
        return repository.save(conversation);
    }
    
    @Override
    public Conversation addMessageId(String conversationId, String messageId) {
        Conversation conversation = findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        conversation.getMessageIds().add(messageId);
        conversation.setLastActivityTime(LocalDateTime.now());
        
        return repository.save(conversation);
    }
    
    @Override
    public Conversation close(String id) {
        Conversation conversation = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        conversation.setStatus(ConversationStatus.CLOSED);
        conversation.setEndTime(LocalDateTime.now());
        
        return repository.save(conversation);
    }
} 