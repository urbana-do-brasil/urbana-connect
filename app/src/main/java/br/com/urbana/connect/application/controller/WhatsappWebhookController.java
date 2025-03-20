package br.com.urbana.connect.application.controller;

import br.com.urbana.connect.domain.port.input.WebhookUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador responsável por receber as notificações do webhook do WhatsApp
 * e verificar tokens para validação.
 */
@RestController
@RequestMapping("/api/webhook/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsappWebhookController {

    private final WebhookUseCase webhookService;

    /**
     * Endpoint GET para verificação do webhook pelo WhatsApp.
     * É chamado quando o webhook está sendo configurado pela plataforma WhatsApp.
     *
     * @param mode      Modo de desafio, deve ser "subscribe"
     * @param token     Token para verificação
     * @param challenge Desafio que deve ser retornado para verificação
     * @return HTTP 200 com o desafio se verificado, HTTP 403 caso contrário
     */
    @GetMapping
    public ResponseEntity<Object> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        
        log.info("Recebido pedido de verificação do webhook WhatsApp. Mode: {}, Token: {}", mode, token);
        
        String verifiedChallenge = webhookService.verifyWebhook(token, challenge);
        if (verifiedChallenge != null) {
            log.info("Webhook do WhatsApp verificado com sucesso. Retornando desafio: {}", challenge);
            return ResponseEntity.ok(verifiedChallenge);
        } else {
            log.warn("Falha na verificação do webhook do WhatsApp. Token inválido ou modo inválido.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Endpoint POST para receber notificações de mensagens do WhatsApp.
     * Processa os eventos recebidos e encaminha para o caso de uso apropriado.
     *
     * @param payload Payload JSON recebido do webhook do WhatsApp
     * @return HTTP 200 OK para confirmar recebimento
     */
    @PostMapping
    public ResponseEntity<String> receiveNotification(@RequestBody String payload) {
        try {
            log.info("Recebida notificação do webhook WhatsApp");
            log.debug("Payload do webhook: {}", payload);
            
            webhookService.processWebhookNotification(payload);
            
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (Exception e) {
            log.error("Erro ao processar notificação do webhook: {}", e.getMessage(), e);
            // Ainda retornamos 200 para o WhatsApp não reenviar a mensagem
            return ResponseEntity.ok("EVENT_RECEIVED");
        }
    }
} 