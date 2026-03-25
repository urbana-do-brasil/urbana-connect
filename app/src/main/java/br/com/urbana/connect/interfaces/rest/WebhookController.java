package br.com.urbana.connect.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

@RestController
public class WebhookController {

    @PostMapping("/api/webhook")
    public ResponseEntity<Void> receive(@RequestBody JsonNode payload) {
        return ResponseEntity.ok().build();
    }
}
