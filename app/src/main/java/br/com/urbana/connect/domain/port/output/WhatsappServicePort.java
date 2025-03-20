package br.com.urbana.connect.domain.port.output;

import br.com.urbana.connect.domain.model.Message;

/**
 * Interface para operações de comunicação com a API do WhatsApp.
 * Na arquitetura hexagonal, representa uma porta de saída.
 */
public interface WhatsappServicePort {
    
    /**
     * Envia uma mensagem de texto para um número de telefone via WhatsApp.
     * 
     * @param phoneNumber Número de telefone do destinatário
     * @param textContent Conteúdo da mensagem
     * @return ID da mensagem no WhatsApp
     */
    String sendTextMessage(String phoneNumber, String textContent);
    
    /**
     * Envia uma mensagem para um número de telefone via WhatsApp.
     * 
     * @param phoneNumber Número de telefone do destinatário
     * @param message Mensagem a ser enviada
     * @return ID da mensagem no WhatsApp
     */
    String sendMessage(String phoneNumber, Message message);
    
    /**
     * Marca uma mensagem como lida no WhatsApp.
     * 
     * @param whatsappMessageId ID da mensagem no WhatsApp
     * @return true se a operação foi bem-sucedida
     */
    boolean markMessageAsRead(String whatsappMessageId);
    
    /**
     * Verifica a validade de um token do webhook do WhatsApp.
     * 
     * @param token Token a ser verificado
     * @param challenge Desafio fornecido pelo WhatsApp
     * @return true se o token é válido
     */
    boolean verifyWebhook(String token, String challenge);
    
    /**
     * Processa uma notificação recebida do webhook do WhatsApp.
     * 
     * @param payload Payload JSON da notificação
     * @return Mensagem processada ou null se não for uma mensagem
     */
    Message processWebhookNotification(String payload);
} 