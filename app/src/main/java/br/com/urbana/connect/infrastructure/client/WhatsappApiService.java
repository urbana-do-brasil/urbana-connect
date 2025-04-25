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
import java.util.HashMap;
import java.util.Map;
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
    private final WebhookPayloadProcessor webhookPayloadProcessor;
    
    /**
     * Construtor com injeção de dependências.
     */
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
        this.webhookPayloadProcessor = new WebhookPayloadProcessor(objectMapper);
        
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
            messageNode.put("to", sanitizedPhone);
            messageNode.put("type", "text");
            
            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("body", textContent);
            
            messageNode.set("text", textNode);
            
            // Configurar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);
            
            // Enviar requisição
            String url = apiUrl + "/" + phoneNumberId + "/messages";
            
            // Adicionar log detalhado da estrutura da mensagem
            log.info("Payload completo da mensagem para WhatsApp: {}", objectMapper.writeValueAsString(messageNode));
            log.info("Headers da requisição: {}", headers);
            log.info("URL de envio: {}", url);
            
            // Enviar o objeto diretamente em vez de convertê-lo para string
            HttpEntity<ObjectNode> entity = new HttpEntity<>(messageNode, headers);
            
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
            // Criar payload usando ObjectNode
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("messaging_product", "whatsapp");
            payload.put("status", "read");
            payload.put("message_id", whatsappMessageId);
            
            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.debug("Payload JSON para marcar mensagem como lida: {}", jsonPayload);
            
            // Configurar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);
            
            // Enviar requisição para o endpoint correto
            String url = apiUrl + "/" + phoneNumberId + "/messages";
            log.debug("URL para marcar mensagem como lida: {}", url);
            
            // Adicionar log detalhado da estrutura do payload
            log.info("Payload completo para marcar mensagem como lida: {}", jsonPayload);
            log.info("Headers da requisição: {}", headers);
            log.info("URL de envio: {}", url);
            
            // Enviar o objeto diretamente em vez de convertê-lo para string
            HttpEntity<ObjectNode> entity = new HttpEntity<>(payload, headers);
            
            log.debug("Enviando requisição para marcar mensagem como lida");
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            
            if (success) {
                log.info("Mensagem marcada como lida com sucesso: {}", whatsappMessageId);
            } else {
                log.error("Erro ao marcar mensagem como lida. Status: {}. Resposta: {}", 
                          response.getStatusCode(), response.getBody());
            }
            
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
        return webhookPayloadProcessor.processPayload(payload);
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