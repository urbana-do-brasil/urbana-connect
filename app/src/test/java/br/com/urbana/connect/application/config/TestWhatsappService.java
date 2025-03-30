package br.com.urbana.connect.application.config;

import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.port.output.WhatsappServicePort;
import br.com.urbana.connect.infrastructure.client.WebhookPayloadProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Implementação de teste do serviço WhatsApp para uso em testes integrados.
 * Simula comportamentos sem fazer chamadas externas.
 */
public class TestWhatsappService implements WhatsappServicePort {

    private final String verifyToken;
    private final ObjectMapper objectMapper;
    private final WebhookPayloadProcessor webhookPayloadProcessor;
    private static final Logger logger = Logger.getLogger(TestWhatsappService.class.getName());

    /**
     * Construtor para a implementação de teste.
     *
     * @param verifyToken Token de verificação de webhook
     * @param objectMapper Mapper para JSON
     */
    public TestWhatsappService(String verifyToken, ObjectMapper objectMapper) {
        this.verifyToken = verifyToken;
        this.objectMapper = objectMapper;
        this.webhookPayloadProcessor = new WebhookPayloadProcessor(objectMapper);
        logger.info("TestWhatsappService inicializado com token: " + verifyToken);
    }

    @Override
    public String sendTextMessage(String phoneNumber, String textContent) {
        logger.info("Simulando envio de mensagem para " + phoneNumber + ": " + textContent);
        // Gerar um ID de mensagem falso para simular resposta da API
        return "test_msg_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public String sendMessage(String phoneNumber, Message message) {
        logger.info("Simulando envio de mensagem para " + phoneNumber);
        return sendTextMessage(phoneNumber, message.getContent());
    }

    @Override
    public boolean markMessageAsRead(String whatsappMessageId) {
        logger.fine("Simulando marcação de mensagem como lida: " + whatsappMessageId);
        // Sempre retornar sucesso na simulação
        return true;
    }
    
    @Override
    public boolean verifyWebhook(String token, String challenge) {
        logger.fine("Verificando token de webhook de teste: " + token);
        return token != null && token.equals(verifyToken);
    }
    
    @Override
    public Message processWebhookNotification(String payload) {
        logger.fine("Processando payload de teste: " + payload);
        return webhookPayloadProcessor.processPayload(payload);
    }
} 