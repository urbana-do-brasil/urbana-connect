package br.com.urbana.connect.application.config;

import br.com.urbana.connect.domain.enums.MessageStatus;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.port.output.WhatsappServicePort;
import br.com.urbana.connect.domain.service.MessageService;
import br.com.urbana.connect.infrastructure.client.WebhookPayloadProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
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
    private final MessageService messageService;
    private static final Logger logger = Logger.getLogger(TestWhatsappService.class.getName());

    /**
     * Construtor para a implementação de teste.
     *
     * @param verifyToken Token de verificação de webhook
     * @param objectMapper Mapper para JSON
     * @param messageService Serviço de mensagens para processamento de status
     */
    public TestWhatsappService(String verifyToken, ObjectMapper objectMapper, MessageService messageService) {
        this.verifyToken = verifyToken;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
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
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            
            // Verificar se é um payload de atualização de status
            if (isStatusUpdatePayload(rootNode)) {
                processStatusUpdate(rootNode);
                return null;
            }
            
            // Processar payload regular de mensagem
            return webhookPayloadProcessor.processPayload(payload);
        } catch (IOException e) {
            logger.warning("Erro ao processar payload: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Verifica se o payload é uma atualização de status.
     */
    private boolean isStatusUpdatePayload(JsonNode rootNode) {
        if (!rootNode.has("entry") || rootNode.path("entry").isEmpty()) {
            return false;
        }
        
        JsonNode valueNode = rootNode.path("entry").path(0).path("changes").path(0).path("value");
        return valueNode.has("statuses") && !valueNode.path("statuses").isEmpty();
    }
    
    /**
     * Processa uma atualização de status.
     */
    private void processStatusUpdate(JsonNode rootNode) {
        try {
            JsonNode statusNode = rootNode.path("entry").path(0)
                    .path("changes").path(0).path("value")
                    .path("statuses").path(0);
            
            String messageId = statusNode.path("id").asText();
            String status = statusNode.path("status").asText();
            
            logger.info("Processando atualização de status: messageId=" + messageId + ", status=" + status);
            
            // Se o serviço de mensagens estiver disponível, atualizar o status
            if (messageService != null) {
                if ("read".equals(status)) {
                    messageService.processReadReceipt(messageId);
                } else if ("delivered".equals(status)) {
                    // Encontrar a mensagem pelo ID do WhatsApp e então atualizar o status
                    // Isso seria implementado melhor na aplicação real
                    logger.info("Mensagem marcada como entregue: " + messageId);
                }
            }
        } catch (Exception e) {
            logger.warning("Erro ao processar atualização de status: " + e.getMessage());
        }
    }
} 