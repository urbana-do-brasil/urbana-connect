package br.com.urbana.connect.domain.port.output;

import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.enums.MessageStatus;

import java.util.List;
import java.util.Optional;

/**
 * Interface para operações relacionadas a mensagens no repositório.
 * Na arquitetura hexagonal, representa uma porta de saída.
 */
public interface MessageRepository {
    
    /**
     * Salva uma mensagem no repositório.
     * 
     * @param message Mensagem a ser salva
     * @return Mensagem salva com ID gerado
     */
    Message save(Message message);
    
    /**
     * Busca uma mensagem pelo ID.
     * 
     * @param id ID da mensagem
     * @return Mensagem encontrada ou vazio se não existir
     */
    Optional<Message> findById(String id);
    
    /**
     * Lista todas as mensagens de uma conversa em ordem cronológica.
     * 
     * @param conversationId ID da conversa
     * @return Lista de mensagens ordenadas por timestamp
     */
    List<Message> findByConversationId(String conversationId);
    
    /**
     * Lista todas as mensagens de um cliente.
     * 
     * @param customerId ID do cliente
     * @return Lista de mensagens do cliente
     */
    List<Message> findByCustomerId(String customerId);
    
    /**
     * Atualiza o status de uma mensagem.
     * 
     * @param id ID da mensagem
     * @param status Novo status
     * @return Mensagem atualizada
     */
    Message updateStatus(String id, MessageStatus status);
    
    /**
     * Busca uma mensagem pelo ID recebido do WhatsApp.
     * 
     * @param whatsappMessageId ID da mensagem no WhatsApp
     * @return Mensagem encontrada ou vazio se não existir
     */
    Optional<Message> findByWhatsappMessageId(String whatsappMessageId);
} 