package br.com.urbana.connect.infrastructure.hermes;

import br.com.urbana.connect.domain.reception.port.out.HermesResumeGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP adapter for the versioned PEE-103 internal resume endpoints. */
@Component
@ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
public final class HttpHermesResumeGateway implements HermesResumeGateway {
    private final RestClient client;
    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpHermesResumeGateway(RestClient.Builder builder,
                                   @Value("${hermes.sessions.base-url:http://127.0.0.1:8642}") String baseUrl,
                                   @Value("${hermes.sessions.api-server-key:}") String apiKey) {
        this.client = builder.build();
        String configuredBaseUrl = baseUrl == null || baseUrl.isBlank()
                ? "http://127.0.0.1:8642" : baseUrl.trim();
        this.baseUrl = configuredBaseUrl.endsWith("/")
                ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1) : configuredBaseUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public ContextSyncReceipt synchronize(String sessionId, ResumeContext context) {
        JsonNode body = post(sessionId, "context-sync", context);
        return new ContextSyncReceipt(text(body, "resumeId"), text(body, "lineageId"),
                text(body, "effectiveSessionId"), text(body, "checksum"),
                body.path("cursor").asLong(), body.path("coveredThroughSequence").asInt());
    }

    @Override
    public ResumeDecision decide(String sessionId, ResumeCommand command) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("effectiveSessionId", command.contextReceipt().effectiveSessionId());
        receipt.put("checksum", command.contextReceipt().checksum());
        receipt.put("cursor", command.contextReceipt().cursor());
        receipt.put("coveredThroughSequence", command.contextReceipt().coveredThroughSequence());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractVersion", command.contractVersion());
        payload.put("resumeId", command.resumeId());
        payload.put("lineageId", command.lineageId());
        payload.put("idempotencyKey", command.idempotencyKey());
        payload.put("contextReceipt", receipt);
        payload.put("directive", command.directive());
        JsonNode body = post(sessionId, "resume-decide", payload);
        return new ResumeDecision(text(body, "resumeId"), text(body, "effectiveSessionId"),
                Action.valueOf(text(body, "action")), text(body, "nextStep"),
                body.path("message").isNull() ? null : text(body, "message"),
                mapper.convertValue(body.path("evidenceMessageIds"),
                        mapper.getTypeFactory().constructCollectionType(java.util.List.class, String.class)),
                text(body, "reasonCode"), body.path("confidence").asDouble());
    }

    private JsonNode post(String sessionId, String operation, Object payload) {
        try {
            String body = client.post()
                    .uri(baseUrl + "/api/internal/v1/sessions/" + sessionId.replace("/", "%2F") + "/" + operation)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(String.class);
            return mapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException failure) {
            throw new ResumeGatewayException("Retomada indisponível.", failure.getStatusCode().value(), failure);
        } catch (Exception failure) {
            throw new ResumeGatewayException("Retomada indisponível.", 0, failure);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new ResumeGatewayException("Resposta de retomada inválida.", 0, null);
        }
        return value.asText();
    }

    public static final class ResumeGatewayException extends RuntimeException {
        private final int status;
        ResumeGatewayException(String message, int status, Throwable cause) {
            super(message, cause);
            this.status = status;
        }
        public int status() { return status; }
    }
}
