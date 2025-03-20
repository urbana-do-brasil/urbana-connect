package br.com.urbana.connect.domain.port.output;

import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.enums.ConversationStatus;

import java.util.List;
import java.util.Optional;

/**
 * Interface para operações de persistência de conversas.
 * Na arquitetura hexagonal, representa uma porta de saída.
 */
public interface ConversationRepository {
    
    /**
     * Salva uma conversa.
     * 
     * @param conversation Conversa a ser salva
     * @return Conversa persistida com ID gerado
     */
    Conversation save(Conversation conversation);
    
    /**
     * Busca uma conversa pelo ID.
     * 
     * @param id ID da conversa
     * @return Conversa encontrada ou vazio se não existir
     */
    Optional<Conversation> findById(String id);
    
    /**
     * Lista conversas pelo ID do cliente.
     * 
     * @param customerId ID do cliente
     * @return Lista de conversas do cliente
     */
    List<Conversation> findByCustomerId(String customerId);
    
    /**
     * Busca conversas ativas de um cliente.
     * 
     * @param customerId ID do cliente
     * @param status Status da conversa
     * @return Lista de conversas ativas do cliente
     */
    List<Conversation> findByCustomerIdAndStatus(String customerId, ConversationStatus status);
    
    /**
     * Atualiza o status de uma conversa.
     * 
     * @param id ID da conversa
     * @param status Novo status
     * @return Conversa atualizada
     */
    Conversation updateStatus(String id, ConversationStatus status);
    
    /**
     * Adiciona um ID de mensagem a uma conversa.
     * 
     * @param conversationId ID da conversa
     * @param messageId ID da mensagem
     * @return Conversa atualizada
     */
    Conversation addMessageId(String conversationId, String messageId);
    
    /**
     * Marca uma conversa como finalizada.
     * 
     * @param id ID da conversa
     * @return Conversa finalizada
     */
    Conversation close(String id);
} 