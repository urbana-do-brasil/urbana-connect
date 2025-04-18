package br.com.urbana.connect.domain.port.input;

import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.enums.MessageStatus;

/**
 * Interface que define os casos de uso para processamento de mensagens.
 * Seguindo o padrão de arquitetura hexagonal, esta é uma porta de entrada.
 */
public interface MessageProcessingUseCase {
    
    /**
     * Recebe e processa uma mensagem de entrada de um cliente.
     * 
     * @param inboundMessage Mensagem recebida do cliente via WhatsApp
     * @return Mensagem de resposta a ser enviada ao cliente
     */
    Message processInboundMessage(Message inboundMessage);
    
    /**
     * Processa uma mensagem recebida pelo número de telefone.
     *
     * @param phoneNumber Número de telefone do cliente
     * @param messageContent Conteúdo da mensagem
     * @param whatsappMessageId ID da mensagem no WhatsApp (opcional)
     * @return Resposta gerada para a mensagem
     */
    String processIncomingMessage(String phoneNumber, String messageContent, String whatsappMessageId);
    
    /**
     * Gera uma resposta baseada no histórico da conversa e na última mensagem recebida.
     * 
     * @param conversationId ID da conversa
     * @param messageId ID da última mensagem
     * @return Mensagem de resposta gerada
     */
    Message generateResponse(String conversationId, String messageId);
    
    /**
     * Transfere uma conversa para atendimento humano.
     * 
     * @param conversationId ID da conversa a ser transferida
     * @param reason Motivo da transferência
     * @return true se a transferência foi bem-sucedida
     */
    boolean transferToHuman(String conversationId, String reason);
    
    /**
     * Processa mensagens de status do WhatsApp (entregue, lida, etc).
     * 
     * @param messageId ID da mensagem
     * @param status Novo status da mensagem
     * @return true se o status foi atualizado com sucesso
     */
    boolean processMessageStatusUpdate(String messageId, MessageStatus status);
    
    /**
     * Processa uma notificação de leitura de mensagem do WhatsApp.
     *
     * @param whatsappMessageId ID da mensagem no WhatsApp
     * @return true se processado com sucesso, false caso contrário
     */
    boolean processReadReceipt(String whatsappMessageId);
} 