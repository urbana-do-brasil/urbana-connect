package br.com.urbana.connect.interfaces.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

@RestController
public class WebhookController {

    private static final String WHATSAPP_BUSINESS_ACCOUNT = "whatsapp_business_account";

    private final String verifyToken;

    public WebhookController(@Value("${whatsapp.webhook.verify-token:}") String verifyToken) {
        this.verifyToken = verifyToken;
    }

    @GetMapping("/api/webhook")
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String providedToken,
            @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && verifyToken.equals(providedToken)) {
            return ResponseEntity.ok(challenge);
        }

        return ResponseEntity.status(403).build();
    }

    @PostMapping("/api/webhook")
    public ResponseEntity<Void> receive(@RequestBody JsonNode payload) {
        if (!WHATSAPP_BUSINESS_ACCOUNT.equals(payload.path("object").asText())) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }
}
