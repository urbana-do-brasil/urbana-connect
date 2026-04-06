package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.conversation.ConversationFlowService;
import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
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
    private static final String ENTRY_FIELD = "entry";
    private static final String CHANGES_FIELD = "changes";
    private static final String VALUE_FIELD = "value";
    private static final String MESSAGES_FIELD = "messages";
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final String verifyToken;
    private final ConversationFlowService conversationFlowService;

    public WebhookController(
            @Value("${whatsapp.webhook.verify-token:}") String verifyToken,
            ConversationFlowService conversationFlowService) {
        this.verifyToken = verifyToken;
        this.conversationFlowService = conversationFlowService;
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
        int entriesCount = payload.path(ENTRY_FIELD).isArray() ? payload.path(ENTRY_FIELD).size() : 0;

        if (!WHATSAPP_BUSINESS_ACCOUNT.equals(object)) {
            log.warn("Webhook rejeitado: object={} entries={}", object, entriesCount);
            return ResponseEntity.badRequest().build();
        }

        log.info("Webhook recebido: object={} entries={}", object, entriesCount);
        dispatchIncomingMessages(payload, Instant.now());
        return ResponseEntity.ok().build();
    }

    private void dispatchIncomingMessages(JsonNode payload, Instant receivedAt) {
        JsonNode entries = payload.path(ENTRY_FIELD);
        if (!entries.isArray()) {
            return;
        }

        for (JsonNode entry : entries) {
            dispatchEntryMessages(entry, receivedAt);
        }
    }

    private void dispatchEntryMessages(JsonNode entry, Instant receivedAt) {
        JsonNode changes = entry.path(CHANGES_FIELD);
        if (!changes.isArray()) {
            return;
        }

        for (JsonNode change : changes) {
            dispatchChangeMessages(change, receivedAt);
        }
    }

    private void dispatchChangeMessages(JsonNode change, Instant receivedAt) {
        JsonNode messages = change.path(VALUE_FIELD).path(MESSAGES_FIELD);
        if (!messages.isArray()) {
            return;
        }

        for (JsonNode message : messages) {
            dispatchMessage(message, receivedAt);
        }
    }

    private void dispatchMessage(JsonNode message, Instant receivedAt) {
        String phoneNumber = message.path("from").asText("");
        if (phoneNumber.isBlank()) {
            return;
        }

        conversationFlowService.handleIncomingMessage(
            new InboundWhatsAppMessage(
                phoneNumber,
                message.path("text").path("body").asText(""),
                resolveInteractiveReplyId(message)
            ),
            receivedAt
        );
    }

    private String resolveInteractiveReplyId(JsonNode message) {
        JsonNode interactive = message.path("interactive");
        String buttonReplyId = interactive.path("button_reply").path("id").asText("");
        if (!buttonReplyId.isBlank()) {
            return buttonReplyId;
        }

        return interactive.path("list_reply").path("id").asText("");
    }
}
