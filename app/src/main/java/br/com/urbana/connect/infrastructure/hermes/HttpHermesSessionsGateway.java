package br.com.urbana.connect.infrastructure.hermes;

import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;

/** HTTP adapter for Hermes v2026.8.3 Sessions API. */
public class HttpHermesSessionsGateway implements HermesSessionsGateway {
    private final RestClient client;
    private final String baseUrl;
    private final String apiServerKey;
    private final String model;
    private final String reasoningEffort;
    private final ObjectMapper mapper;

    public HttpHermesSessionsGateway(RestClient.Builder builder, String baseUrl, String apiServerKey,
                                     String model, String reasoningEffort) {
        this(builder.build(), baseUrl, apiServerKey, model, reasoningEffort, new ObjectMapper());
    }

    public HttpHermesSessionsGateway(RestClient.Builder builder, String baseUrl, String apiServerKey,
                                     String model, String reasoningEffort, Duration timeout) {
        this(builder.requestFactory(requestFactory(timeout)).build(), baseUrl, apiServerKey,
                model, reasoningEffort, new ObjectMapper());
    }

    public HttpHermesSessionsGateway(RestClient client, String baseUrl, String apiServerKey,
                                     String model, String reasoningEffort) {
        this(client, baseUrl, apiServerKey, model, reasoningEffort, new ObjectMapper());
    }

    public HttpHermesSessionsGateway(RestClient client, String baseUrl, String apiServerKey,
                                     String model, String reasoningEffort, ObjectMapper mapper) {
        this.client = client;
        this.baseUrl = trimBaseUrl(baseUrl);
        this.apiServerKey = apiServerKey == null ? "" : apiServerKey;
        this.model = model == null || model.isBlank() ? "openai/gpt-5.6-luna" : model;
        this.reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank() ? "max" : reasoningEffort;
        this.mapper = mapper;
    }

    @Override
    public String createSession(String contactId) {
        if (contactId == null || contactId.isBlank()) {
            throw new IllegalArgumentException("contactId is required");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", contactId);
        // Hermes persists the session model at creation time and gives it
        // precedence over per-turn overrides. Sending the real runtime
        // selection prevents the API's virtual `hermes-agent` alias from
        // being persisted and then routed to OpenRouter as a model ID.
        payload.put("model", model);
        payload.put("provider", "openrouter");
        payload.put("model_options", Map.of("reasoning_effort", reasoningEffort));
        ExchangeResponse response = exchange("/api/sessions", payload);
        JsonNode body = response.body();
        String sessionId = text(body.path("session"), "id");
        if (sessionId == null) {
            sessionId = text(body, "session_id", "id");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new HermesSessionsException("Hermes create session did not return session_id", null);
        }
        return sessionId;
    }

    @Override
    public HermesChatResult chat(String sessionId, HermesChatRequest request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        Map<String, Object> modelOptions = Map.of("reasoning_effort", request.reasoningEffort());
        Map<String, Object> payload = new HashMap<>();
        boolean hasSupportedImage = request.images().stream().anyMatch(
                HttpHermesSessionsGateway::isSupportedImageReference);
        payload.put("message", hasSupportedImage
                ? multimodalMessage(request.input(), request.images()) : request.input());
        payload.put("model", request.model() == null ? model : request.model());
        payload.put("provider", request.provider());
        payload.put("model_options", modelOptions);
        ExchangeResponse response = exchange("/api/sessions/" + encode(sessionId) + "/chat", payload);
        JsonNode body = response.body();
        String effectiveId = response.headers().getFirst("X-Hermes-Session-Id");
        if (effectiveId == null || effectiveId.isBlank()) {
            effectiveId = text(body, "session_id", "sessionId");
        }
        if (effectiveId == null || effectiveId.isBlank()) {
            effectiveId = sessionId;
        }
        JsonNode message = body.path("message");
        String content = message.isObject() ? text(message, "content") : text(body, "content");
        if (content == null) {
            throw new HermesSessionsException("Hermes chat did not return message.content", null);
        }
        JsonNode usage = body.path("usage");
        long input = number(usage, "input_tokens", "prompt_tokens");
        long output = number(usage, "output_tokens", "completion_tokens");
        long total = number(usage, "total_tokens");
        AgentUsage agentUsage = new AgentUsage(input, output, total == 0 ? input + output : total);
        Map<String, Object> raw = mapper.convertValue(body, new TypeReference<>() { });
        return new HermesChatResult(sessionId, effectiveId, content, agentUsage, raw);
    }

    @Override
    public List<HermesHistoryMessage> history(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        ExchangeResponse response = exchange("/api/sessions/" + encode(sessionId) + "/messages", null);
        JsonNode body = response.body();
        JsonNode messages = body.isArray() ? body : body.path("data");
        if (!messages.isArray()) {
            messages = body.path("messages");
        }
        List<HermesHistoryMessage> result = new ArrayList<>();
        if (messages.isArray()) {
            for (JsonNode node : messages) {
                String role = text(node, "role");
                String content = text(node, "content");
                if (role != null && content != null) {
                    result.add(new HermesHistoryMessage(role, content));
                }
            }
        }
        return result;
    }

    private ExchangeResponse exchange(String path, Object payload) {
        try {
            RestClient.RequestHeadersSpec<?> request = client.method(payload == null
                    ? org.springframework.http.HttpMethod.GET : org.springframework.http.HttpMethod.POST)
                    .uri(baseUrl + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiServerKey)
                    .accept(MediaType.APPLICATION_JSON);
            if (payload != null) {
                request = ((RestClient.RequestBodySpec) request)
                        .contentType(MediaType.APPLICATION_JSON).body(payload);
            }
            ResponseEntity<String> response = request.retrieve().toEntity(String.class);
            String body = response.getBody();
            return new ExchangeResponse(mapper.readTree(body == null ? "{}" : body), response.getHeaders());
        } catch (RestClientResponseException exception) {
            throw HermesSessionsException.fromStatus(exception.getStatusCode().value(), exception.getResponseBodyAsString());
        } catch (Exception exception) {
            throw new HermesSessionsException("Hermes Sessions API request failed", exception);
        }
    }

    private static List<Map<String, Object>> multimodalMessage(String input, List<String> images) {
        List<Map<String, Object>> parts = new ArrayList<>();
        if (input != null && !input.isBlank()) {
            parts.add(Map.of("type", "text", "text", input));
        }
        for (String image : images) {
            if (!isSupportedImageReference(image)) {
                continue;
            }
            parts.add(Map.of("type", "image_url", "image_url", Map.of("url", image)));
        }
        return parts;
    }

    private static boolean isSupportedImageReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return false;
        }
        return reference.regionMatches(true, 0, "data:image/", 0, 11)
                || reference.regionMatches(true, 0, "https://", 0, 8)
                || reference.regionMatches(true, 0, "http://", 0, 7);
    }

    private record ExchangeResponse(JsonNode body, HttpHeaders headers) { }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    private static long number(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isNumber()) {
                return Math.max(0, value.asLong());
            }
        }
        return 0;
    }

    private static String trimBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8642";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static ClientHttpRequestFactory requestFactory(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Hermes HTTP timeout must be positive");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    private static String encode(String value) {
        return value.replace("/", "%2F");
    }

    public static final class HermesSessionsException extends RuntimeException {
        private final int status;

        public HermesSessionsException(String message, Throwable cause) {
            this(message, cause, 0);
        }

        private HermesSessionsException(String message, Throwable cause, int status) {
            super(message, cause);
            this.status = status;
        }

        public int status() {
            return status;
        }

        public static HermesSessionsException fromStatus(int status, String body) {
            String category = switch (status) {
                case 401, 403 -> "AUTHENTICATION_FAILED";
                case 404 -> "SESSION_NOT_FOUND";
                case 429 -> "CAPACITY_EXCEEDED";
                default -> status >= 500 ? "UPSTREAM_FAILURE" : "HTTP_ERROR";
            };
            return new HermesSessionsException(category + " (HTTP " + status + ")", null, status);
        }
    }
}
