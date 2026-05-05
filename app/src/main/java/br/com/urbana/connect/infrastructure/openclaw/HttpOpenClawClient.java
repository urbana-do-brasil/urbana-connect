package br.com.urbana.connect.infrastructure.openclaw;

import br.com.urbana.connect.domain.conversation.model.OpenClawTurnRequest;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnResult;
import br.com.urbana.connect.domain.conversation.port.out.OpenClawClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

public class HttpOpenClawClient implements OpenClawClient {

    private final RestClient restClient;
    private final String chatCompletionsPath;
    private final String gatewayToken;
    private final String model;

    public HttpOpenClawClient(RestClient restClient, String chatCompletionsPath, String gatewayToken, String model) {
        this.restClient = restClient;
        this.chatCompletionsPath = chatCompletionsPath;
        this.gatewayToken = gatewayToken;
        this.model = model;
    }

    @Override
    public OpenClawTurnResult sendTurn(OpenClawTurnRequest request) {
        try {
            ChatCompletionResponse response = restClient.post()
                .uri(chatCompletionsPath)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (gatewayToken != null && !gatewayToken.isBlank()) {
                        headers.setBearerAuth(gatewayToken);
                    }
                    headers.set("x-openclaw-session-key", request.sessionKey());
                    headers.set("x-openclaw-message-channel", "whatsapp");
                })
                .body(new ChatCompletionRequest(
                    model,
                    false,
                    request.sessionKey(),
                    List.of(new ChatMessage("user", request.text()))
                ))
                .retrieve()
                .body(ChatCompletionResponse.class);

            String text = extractText(response);
            if (text == null || text.isBlank()) {
                return OpenClawTurnResult.error("invalid_gateway_response");
            }
            return OpenClawTurnResult.success(text.trim());
        } catch (ResourceAccessException exception) {
            return OpenClawTurnResult.timeout("client_timeout");
        } catch (RestClientResponseException exception) {
            return OpenClawTurnResult.error("gateway_http_" + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            return OpenClawTurnResult.error("client_error");
        }
    }

    private String extractText(ChatCompletionResponse response) {
        if (response == null || response.choices == null || response.choices.isEmpty()) {
            return null;
        }
        Choice firstChoice = response.choices.getFirst();
        if (firstChoice == null || firstChoice.message == null) {
            return null;
        }
        return firstChoice.message.content;
    }

    private record ChatCompletionRequest(String model, boolean stream, String user, List<ChatMessage> messages) {
    }

    private record ChatMessage(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatCompletionResponse {
        public List<Choice> choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Choice {
        public ChatMessageResponse message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatMessageResponse {
        public String content;
    }
}
