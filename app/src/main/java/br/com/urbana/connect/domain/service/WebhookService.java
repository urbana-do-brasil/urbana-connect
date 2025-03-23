package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.port.input.WebhookUseCase;
import br.com.urbana.connect.domain.port.output.WhatsappServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementação do caso de uso para processamento de webhooks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService implements WebhookUseCase {
    
    private final WhatsappServicePort whatsappServicePort;
    private final MessageService messageService;
    @Override
    public boolean processWebhookNotification(String payload) {
        log.debug("Processando notificação de webhook: {}", payload);
        
        try {
            // Extrair mensagem do payload
            Message message = whatsappServicePort.processWebhookNotification(payload);
            
            if (message == null) {
                log.warn("Não foi possível extrair mensagem do payload");
                return false;
            }
            
            log.info("Mensagem processada com sucesso. ID: {}", message.getId());
            
            // Processar a mensagem
            Message processedMessage = messageService.processInboundMessage(message);

            log.info("Mensagem processada com sucesso. Content: {}", processedMessage.getContent());
            
            // Enviar a resposta para o cliente
            //whatsappServicePort.sendMessage(message.getFrom(), processedMessage.getContent());

            return true;
        } catch (Exception e) {
            log.error("Erro ao processar notificação do webhook: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public String verifyWebhook(String token, String challenge) {
        log.debug("Verificando token de webhook: {}", token);
        
        boolean isValid = whatsappServicePort.verifyWebhook(token, challenge);
        
        if (isValid) {
            log.info("Token do webhook válido");
            return challenge;
        } else {
            log.warn("Token do webhook inválido");
            return null;
        }
    }
} 