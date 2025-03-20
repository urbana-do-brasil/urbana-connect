package br.com.urbana.connect.infrastructure.persistence;

import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.enums.MessageStatus;
import br.com.urbana.connect.domain.port.output.MessageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Implementação do repositório de mensagens usando MongoDB.
 */
@Repository
public class MongoMessageRepository implements MessageRepository {
    
    private final MessageMongoRepository repository;
    
    public MongoMessageRepository(MessageMongoRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public Message save(Message message) {
        return repository.save(message);
    }
    
    @Override
    public Optional<Message> findById(String id) {
        return repository.findById(id);
    }
    
    @Override
    public List<Message> findByConversationId(String conversationId) {
        return repository.findByConversationIdOrderByTimestampAsc(conversationId);
    }
    
    @Override
    public List<Message> findByCustomerId(String customerId) {
        return repository.findByCustomerIdOrderByTimestampDesc(customerId);
    }
    
    @Override
    public Message updateStatus(String id, MessageStatus status) {
        Message message = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada"));
        
        message.setStatus(status);
        return repository.save(message);
    }
    
    @Override
    public Optional<Message> findByWhatsappMessageId(String whatsappMessageId) {
        return repository.findByWhatsappMessageId(whatsappMessageId);
    }
} 