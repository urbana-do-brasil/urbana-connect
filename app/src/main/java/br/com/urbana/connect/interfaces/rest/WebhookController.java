package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.conversation.GreetingFlowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@RestController
public class WebhookController {

    private static final String WHATSAPP_BUSINESS_ACCOUNT = "whatsapp_business_account";
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final String verifyToken;
    private final GreetingFlowService greetingFlowService;

    public WebhookController(
            @Value("${whatsapp.webhook.verify-token:}") String verifyToken,
            GreetingFlowService greetingFlowService) {
        this.verifyToken = verifyToken;
        this.greetingFlowService = greetingFlowService;
    }

    @GetMapping("/api/webhook")
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String providedToken,
            @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && verifyToken.equals(providedToken)) {
            log.info("Webhook challenge aceito: mode={} challengeLength={}", mode, challenge.length());
            return ResponseEntity.ok(challenge);
        }

        log.warn("Webhook challenge rejeitado: mode={}", mode);
        return ResponseEntity.status(403).build();
    }

    @PostMapping("/api/webhook")
    public ResponseEntity<Void> receive(@RequestBody JsonNode payload) {
        String object = payload.path("object").asText();
        int entriesCount = payload.path("entry").isArray() ? payload.path("entry").size() : 0;

        if (!WHATSAPP_BUSINESS_ACCOUNT.equals(object)) {
            log.warn("Webhook rejeitado: object={} entries={}", object, entriesCount);
            return ResponseEntity.badRequest().build();
        }

        log.info("Webhook recebido: object={} entries={}", object, entriesCount);
        dispatchIncomingMessages(payload, Instant.now());
        return ResponseEntity.ok().build();
    }

    private void dispatchIncomingMessages(JsonNode payload, Instant receivedAt) {
        JsonNode entries = payload.path("entry");
        if (!entries.isArray()) {
            return;
        }

        for (JsonNode entry : entries) {
            JsonNode changes = entry.path("changes");
            if (!changes.isArray()) {
                continue;
            }

            for (JsonNode change : changes) {
                JsonNode messages = change.path("value").path("messages");
                if (!messages.isArray()) {
                    continue;
                }

                for (JsonNode message : messages) {
                    String phoneNumber = message.path("from").asText("");
                    if (!phoneNumber.isBlank()) {
                        greetingFlowService.handleIncomingMessage(phoneNumber, receivedAt);
                    }
                }
            }
        }
    }
}
