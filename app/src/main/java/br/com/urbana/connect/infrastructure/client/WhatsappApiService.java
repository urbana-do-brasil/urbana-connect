package br.com.urbana.connect.infrastructure.client;

import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.enums.MessageType;
import br.com.urbana.connect.domain.port.output.WhatsappServicePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Serviço de integração com a API do WhatsApp.
 */
@Service
@Slf4j
public class WhatsappApiService implements WhatsappServicePort {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String phoneNumberId;
    private final String accessToken;
    private final String verifyToken;
    
    public WhatsappApiService(
            @Value("${whatsapp.api-url}") String apiUrl,
            @Value("${whatsapp.phone-number-id}") String phoneNumberId,
            @Value("${whatsapp.access-token}") String accessToken,
            @Value("${whatsapp.verify-token}") String verifyToken,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        
        this.apiUrl = apiUrl;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.verifyToken = verifyToken;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        
        log.info("Inicializando serviço WhatsApp para o número: {}", phoneNumberId);
    }
    
    @Override
    public String sendTextMessage(String phoneNumber, String textContent) {
        log.debug("Enviando mensagem de texto para: {}", phoneNumber);
        
        try {
            // Sanitizar número de telefone
            String sanitizedPhone = sanitizePhoneNumber(phoneNumber);
            
            // Construir payload da mensagem
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("messaging_product", "whatsapp");
            messageNode.put("recipient_type", "individual");
            messageNode.put("to", sanitizedPhone);
            
            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("body", textContent);
            
            messageNode.set("type", objectMapper.valueToTree("text"));
            messageNode.set("text", textNode);
            
            // Configurar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);
            
            // Enviar requisição
            String url = apiUrl + "/" + phoneNumberId + "/messages";
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(messageNode), headers);
            
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, JsonNode.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String messageId = response.getBody().path("messages").path(0).path("id").asText();
                log.info("Mensagem enviada com sucesso. ID: {}", messageId);
                return messageId;
            } else {
                log.error("Falha ao enviar mensagem. Status: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem via WhatsApp: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public String sendMessage(String phoneNumber, Message message) {
        // Por ora, apenas mensagens de texto são suportadas
        if (message.getType() == MessageType.TEXT) {
            return sendTextMessage(phoneNumber, message.getContent());
        } else {
            log.warn("Tipo de mensagem não suportado: {}", message.getType());
            return null;
        }
    }
    
    @Override
    public boolean markMessageAsRead(String whatsappMessageId) {
        log.debug("Marcando mensagem como lida: {}", whatsappMessageId);
        
        try {
            // Construir payload
            ObjectNode readNode = objectMapper.createObjectNode();
            readNode.put("messaging_product", "whatsapp");
            readNode.put("status", "read");
            readNode.put("message_id", whatsappMessageId);
            
            // Configurar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);
            
            // Enviar requisição
            String url = apiUrl + "/" + phoneNumberId + "/messages";
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(readNode), headers);
            
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, JsonNode.class);
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("Marcar mensagem como lida: {}", success ? "sucesso" : "falha");
            
            return success;
        } catch (Exception e) {
            log.error("Erro ao marcar mensagem como lida: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean verifyWebhook(String token, String challenge) {
        log.debug("Verificando webhook com token: {}", token);
        
        boolean isValid = verifyToken.equals(token);
        log.info("Verificação de webhook: {}", isValid ? "válido" : "inválido");
        
        return isValid;
    }
    
    @Override
    public Message processWebhookNotification(String payload) {
        log.debug("Processando notificação do webhook: {}", payload);
        
        try {
            JsonNode root = objectMapper.readTree(payload);
            
            // Verificar se é uma mensagem
            if (!root.has("entry") || !root.path("entry").isArray() || root.path("entry").size() == 0) {
                log.warn("Payload não contém entradas válidas");
                return null;
            }
            
            JsonNode entry = root.path("entry").path(0);
            
            if (!entry.has("changes") || !entry.path("changes").isArray() || entry.path("changes").size() == 0) {
                log.warn("Entry não contém changes válidos");
                return null;
            }
            
            JsonNode change = entry.path("changes").path(0);
            
            if (!change.has("value") || !change.path("value").has("messages") || 
                    !change.path("value").path("messages").isArray() || 
                    change.path("value").path("messages").size() == 0) {
                log.warn("Change não contém mensagens válidas");
                return null;
            }
            
            JsonNode messageNode = change.path("value").path("messages").path(0);
            
            // Extrair dados da mensagem
            String messageId = messageNode.path("id").asText();
            String from = messageNode.path("from").asText();
            String type = messageNode.path("type").asText();
            
            // Extrair conteúdo dependendo do tipo
            String content = "";
            MessageType messageType = MessageType.TEXT; // Padrão
            
            if ("text".equals(type) && messageNode.has("text")) {
                content = messageNode.path("text").path("body").asText();
                messageType = MessageType.TEXT;
            } else if ("image".equals(type)) {
                messageType = MessageType.IMAGE;
                content = "Imagem recebida"; // Simplificação para o MVP
            } else if ("document".equals(type)) {
                messageType = MessageType.DOCUMENT;
                content = "Documento recebido"; // Simplificação para o MVP
            } else {
                content = "Conteúdo não suportado do tipo: " + type;
            }
            
            // Criar objeto de mensagem
            Message message = Message.builder()
                    .whatsappMessageId(messageId)
                    .customerId(from)
                    .type(messageType)
                    .direction(MessageDirection.INBOUND)
                    .content(content)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            log.info("Mensagem processada do webhook. ID: {}, De: {}, Tipo: {}", 
                    messageId, from, messageType);
            
            return message;
        } catch (Exception e) {
            log.error("Erro ao processar notificação do webhook: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Sanitiza o número de telefone para o formato esperado pelo WhatsApp.
     */
    private String sanitizePhoneNumber(String phoneNumber) {
        // Remover caracteres não numéricos
        String sanitized = phoneNumber.replaceAll("[^0-9]", "");
        
        // Adicionar código do país se não tiver
        if (!sanitized.startsWith("55") && sanitized.length() <= 11) {
            sanitized = "55" + sanitized;
        }
        
        return sanitized;
    }
} 