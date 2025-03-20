package br.com.urbana.connect.domain.port.input;

import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.enums.ConversationStatus;

import java.util.List;
import java.util.Optional;

/**
 * Interface que define os casos de uso para gestão de conversas.
 * Seguindo o padrão de arquitetura hexagonal, esta é uma porta de entrada.
 */
public interface ConversationManagementUseCase {
    
    /**
     * Cria uma nova conversa para um cliente.
     * 
     * @param customerId ID do cliente
     * @return Conversa criada
     */
    Conversation createConversation(String customerId);
    
    /**
     * Busca uma conversa pelo ID.
     * 
     * @param conversationId ID da conversa
     * @return Conversa encontrada ou vazio se não existir
     */
    Optional<Conversation> findConversation(String conversationId);
    
    /**
     * Busca a conversa ativa de um cliente.
     * 
     * @param customerId ID do cliente
     * @return Conversa ativa encontrada ou vazio se não existir
     */
    Optional<Conversation> findActiveConversation(String customerId);
    
    /**
     * Adiciona uma mensagem a uma conversa.
     * 
     * @param conversationId ID da conversa
     * @param message Mensagem a ser adicionada
     * @return Conversa atualizada
     */
    Conversation addMessageToConversation(String conversationId, Message message);
    
    /**
     * Atualiza o status de uma conversa.
     * 
     * @param conversationId ID da conversa
     * @param status Novo status
     * @return Conversa atualizada
     */
    Conversation updateConversationStatus(String conversationId, ConversationStatus status);
    
    /**
     * Fecha uma conversa.
     * 
     * @param conversationId ID da conversa
     * @return Conversa fechada
     */
    Conversation closeConversation(String conversationId);
    
    /**
     * Lista o histórico de conversas de um cliente.
     * 
     * @param customerId ID do cliente
     * @return Lista de conversas do cliente
     */
    List<Conversation> listCustomerConversations(String customerId);
    
    /**
     * Recupera o histórico de mensagens de uma conversa.
     * 
     * @param conversationId ID da conversa
     * @return Lista de mensagens da conversa
     */
    List<Message> getConversationMessages(String conversationId);
    
    /**
     * Busca todas as conversas.
     * 
     * @return Lista de todas as conversas
     */
    List<Conversation> findAll();
    
    /**
     * Busca uma conversa pelo ID.
     * Equivalente a findConversation, mantido para compatibilidade.
     * 
     * @param id ID da conversa
     * @return Conversa encontrada ou vazio se não existir
     */
    Optional<Conversation> findById(String id);
    
    /**
     * Busca conversas pelo ID do cliente.
     * Equivalente a listCustomerConversations, mantido para compatibilidade.
     * 
     * @param customerId ID do cliente
     * @return Lista de conversas do cliente
     */
    List<Conversation> findByCustomerId(String customerId);
    
    /**
     * Busca conversas pelo status.
     * 
     * @param status Status da conversa
     * @return Lista de conversas com o status especificado
     */
    List<Conversation> findByStatus(ConversationStatus status);
    
    /**
     * Busca conversas pelo ID do cliente e status.
     * 
     * @param customerId ID do cliente
     * @param status Status da conversa
     * @return Lista de conversas do cliente com o status especificado
     */
    List<Conversation> findByCustomerIdAndStatus(String customerId, ConversationStatus status);
} 