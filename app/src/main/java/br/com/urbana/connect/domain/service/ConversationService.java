package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.enums.ConversationStatus;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.port.input.ConversationManagementUseCase;
import br.com.urbana.connect.domain.port.output.ConversationRepository;
import br.com.urbana.connect.domain.port.output.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementação do caso de uso de gerenciamento de conversas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService implements ConversationManagementUseCase {
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    
    @Override
    public Conversation createConversation(String customerId) {
        log.debug("Criando nova conversa para o cliente: {}", customerId);
        
        // Verificar se já existe uma conversa ativa
        Optional<Conversation> activeConversation = findActiveConversation(customerId);
        if (activeConversation.isPresent()) {
            log.info("Cliente já possui uma conversa ativa. ID: {}", activeConversation.get().getId());
            return activeConversation.get();
        }
        
        // Criar nova conversa
        Conversation conversation = Conversation.builder()
                .customerId(customerId)
                .status(ConversationStatus.ACTIVE)
                .startTime(LocalDateTime.now())
                .lastActivityTime(LocalDateTime.now())
                .handedOffToHuman(false)
                .build();
        
        Conversation savedConversation = conversationRepository.save(conversation);
        log.info("Conversa criada com sucesso. ID: {}", savedConversation.getId());
        
        return savedConversation;
    }
    
    @Override
    public Optional<Conversation> findConversation(String conversationId) {
        log.debug("Buscando conversa pelo ID: {}", conversationId);
        return conversationRepository.findById(conversationId);
    }
    
    @Override
    public Optional<Conversation> findActiveConversation(String customerId) {
        log.debug("Buscando conversa ativa para o cliente: {}", customerId);
        List<Conversation> activeConversations = conversationRepository
                .findByCustomerIdAndStatus(customerId, ConversationStatus.ACTIVE);
        
        return activeConversations.isEmpty() 
                ? Optional.empty() 
                : Optional.of(activeConversations.get(0));
    }
    
    @Override
    public Conversation addMessageToConversation(String conversationId, Message message) {
        log.debug("Adicionando mensagem à conversa: {}", conversationId);
        
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        // Salvar a mensagem
        message.setConversationId(conversationId);
        message.setTimestamp(LocalDateTime.now());
        Message savedMessage = messageRepository.save(message);
        
        // Atualizar a conversa
        conversation.setLastActivityTime(LocalDateTime.now());
        conversationRepository.addMessageId(conversationId, savedMessage.getId());
        
        log.info("Mensagem adicionada à conversa com sucesso. Conversa ID: {}, Mensagem ID: {}", 
                conversationId, savedMessage.getId());
        
        return conversation;
    }
    
    @Override
    public Conversation updateConversationStatus(String conversationId, ConversationStatus status) {
        log.debug("Atualizando status da conversa: {} para {}", conversationId, status);
        
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        conversation.setStatus(status);
        conversation.setLastActivityTime(LocalDateTime.now());
        
        Conversation updatedConversation = conversationRepository.save(conversation);
        log.info("Status da conversa atualizado com sucesso. ID: {}, Status: {}", 
                updatedConversation.getId(), updatedConversation.getStatus());
        
        return updatedConversation;
    }
    
    @Override
    public Conversation closeConversation(String conversationId) {
        log.debug("Fechando conversa: {}", conversationId);
        
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        conversation.setStatus(ConversationStatus.CLOSED);
        conversation.setEndTime(LocalDateTime.now());
        
        Conversation closedConversation = conversationRepository.save(conversation);
        log.info("Conversa fechada com sucesso. ID: {}", closedConversation.getId());
        
        return closedConversation;
    }
    
    @Override
    public List<Conversation> listCustomerConversations(String customerId) {
        log.debug("Listando conversas do cliente: {}", customerId);
        return conversationRepository.findByCustomerId(customerId);
    }
    
    @Override
    public List<Message> getConversationMessages(String conversationId) {
        log.debug("Buscando mensagens da conversa: {}", conversationId);
        
        Optional<Conversation> conversationOpt = conversationRepository.findById(conversationId);
        if (conversationOpt.isEmpty()) {
            log.warn("Conversa não encontrada: {}", conversationId);
            return Collections.emptyList();
        }
        
        return messageRepository.findByConversationId(conversationId);
    }
    
    // Implementações para os métodos adicionados de ConversationQueryUseCase
    
    @Override
    public List<Conversation> findAll() {
        log.debug("Buscando todas as conversas");
        // Como não temos um método findAll no repositório, podemos simular com uma lista vazia por enquanto
        return List.of();
    }
    
    @Override
    public Optional<Conversation> findById(String id) {
        log.debug("Buscando conversa pelo ID: {}", id);
        return findConversation(id);
    }
    
    @Override
    public List<Conversation> findByCustomerId(String customerId) {
        log.debug("Buscando conversas para o cliente: {}", customerId);
        return listCustomerConversations(customerId);
    }
    
    @Override
    public List<Conversation> findByStatus(ConversationStatus status) {
        log.debug("Buscando conversas pelo status: {}", status);
        // Como não temos um método findByStatus no repositório, podemos retornar uma lista vazia
        return List.of();
    }
    
    @Override
    public List<Conversation> findByCustomerIdAndStatus(String customerId, ConversationStatus status) {
        log.debug("Buscando conversas para o cliente {} com status {}", customerId, status);
        return conversationRepository.findByCustomerIdAndStatus(customerId, status);
    }
} 