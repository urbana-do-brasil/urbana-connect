package br.com.urbana.connect.infrastructure.openclaw;

import br.com.urbana.connect.domain.conversation.model.OpenClawTurnRequest;
import br.com.urbana.connect.domain.conversation.model.OpenClawTurnResult;
import br.com.urbana.connect.domain.conversation.port.out.OpenClawClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class HttpOpenClawClient implements OpenClawClient {

    private final RestClient restClient;
    private final String turnPath;

    public HttpOpenClawClient(RestClient restClient, String turnPath) {
        this.restClient = restClient;
        this.turnPath = turnPath;
    }

    @Override
    public OpenClawTurnResult sendTurn(OpenClawTurnRequest request) {
        try {
            BridgeResponse response = restClient.post()
                .uri(turnPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(BridgeResponse.class);

            if (response == null || response.status == null) {
                return OpenClawTurnResult.error("invalid_bridge_response");
            }
            return switch (response.status) {
                case "ok" -> OpenClawTurnResult.success(response.text);
                case "timeout" -> OpenClawTurnResult.timeout(blankToDefault(response.errorReason, "bridge_timeout"));
                default -> OpenClawTurnResult.error(blankToDefault(response.errorReason, "bridge_error"));
            };
        } catch (ResourceAccessException exception) {
            return OpenClawTurnResult.timeout("client_timeout");
        } catch (RestClientException exception) {
            return OpenClawTurnResult.error("client_error");
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BridgeResponse {
        public String text;
        public String status;
        public String errorReason;
    }
}
