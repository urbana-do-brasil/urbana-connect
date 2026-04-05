package br.com.urbana.connect.infrastructure.whatsapp;

import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

public class WhatsAppCloudApiGateway implements WhatsAppMessageGateway {

    private static final String GREETING_TEXT = "Precisando de ajuda para encontrar o servico perfeito?";

    private final RestClient restClient;
    private final String phoneNumberId;
    private final String accessToken;

    public WhatsAppCloudApiGateway(RestClient restClient, String phoneNumberId, String accessToken) {
        this.restClient = restClient;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
    }

    @Override
    public void sendGreeting(String phoneNumber) {
        restClient.post()
            .uri("/v18.0/{phoneNumberId}/messages", phoneNumberId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(buildGreetingPayload(phoneNumber))
            .retrieve()
            .toBodilessEntity();
    }

    private Map<String, Object> buildGreetingPayload(String phoneNumber) {
        return Map.of(
            "messaging_product", "whatsapp",
            "to", phoneNumber,
            "type", "interactive",
            "interactive", Map.of(
                "type", "button",
                "body", Map.of("text", GREETING_TEXT),
                "action", Map.of(
                    "buttons", List.of(
                        replyButton("YES_HELP", "Sim, estou precisando"),
                        replyButton("NO_HELP", "Nao, ja sei o que quero")
                    )
                )
            )
        );
    }

    private Map<String, Object> replyButton(String id, String title) {
        return Map.of(
            "type", "reply",
            "reply", Map.of(
                "id", id,
                "title", title
            )
        );
    }
}
