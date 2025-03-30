package br.com.urbana.connect.infrastructure.client;

import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.enums.MessageStatus;
import br.com.urbana.connect.domain.enums.MessageType;
import br.com.urbana.connect.domain.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Classe utilitária para processamento de payloads de webhook do WhatsApp.
 * Extrai a lógica comum entre a implementação real e a de teste.
 */
public class WebhookPayloadProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(WebhookPayloadProcessor.class);
    private final ObjectMapper objectMapper;
    
    public WebhookPayloadProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Processa um payload de webhook do WhatsApp e extrai uma mensagem.
     * 
     * @param payload JSON payload do webhook
     * @return Objeto Message extraído ou null se não for uma mensagem válida
     */
    public Message processPayload(String payload) {
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            
            // Verificar se é um payload válido
            if (!rootNode.has("entry") || rootNode.path("entry").isEmpty() ||
                !rootNode.path("entry").path(0).has("changes") || 
                rootNode.path("entry").path(0).path("changes").isEmpty()) {
                log.warn("Payload inválido");
                return null;
            }
            
            // Obter os dados da mensagem
            JsonNode entryNode = rootNode.path("entry").path(0);
            JsonNode valueNode = entryNode.path("changes").path(0).path("value");
            
            // Verificar se há mensagens
            if (!valueNode.has("messages") || valueNode.path("messages").isEmpty()) {
                log.warn("Não há mensagens no payload");
                return null;
            }
            
            // Obter a mensagem
            JsonNode messageNode = valueNode.path("messages").path(0);
            
            if (messageNode.has("type") && messageNode.path("type").asText().equals("text")) {
                // Processar mensagem de texto
                String messageId = messageNode.path("id").asText();
                String phoneNumber = messageNode.path("from").asText();
                String content = messageNode.path("text").path("body").asText();
                long timestamp = messageNode.path("timestamp").asLong();
                
                // Converter timestamp para LocalDateTime
                LocalDateTime messageTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(timestamp), 
                        ZoneId.systemDefault());
                
                // Criar a mensagem usando o padrão builder
                Message message = Message.builder()
                        .id(UUID.randomUUID().toString())
                        .whatsappMessageId(messageId)
                        .direction(MessageDirection.INBOUND)
                        .customerId(phoneNumber) // Temporariamente usamos o telefone como ID do cliente
                        .type(MessageType.TEXT)
                        .content(content)
                        .status(MessageStatus.SENT) // Mensagens recebidas são marcadas como SENT
                        .timestamp(messageTime)
                        .build();
                
                log.info("Mensagem processada: from={}, content={}", phoneNumber, content);
                return message;
            } else {
                log.warn("Tipo de mensagem não suportado");
                return null;
            }
        } catch (IOException e) {
            log.error("Erro ao processar payload do webhook: {}", e.getMessage(), e);
            return null;
        }
    }
} 