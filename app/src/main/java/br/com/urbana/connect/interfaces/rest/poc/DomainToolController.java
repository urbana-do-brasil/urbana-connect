package br.com.urbana.connect.interfaces.rest.poc;

import br.com.urbana.connect.application.reception.tools.DomainToolInvocationUseCase;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/internal/poc/domain-tools")
@ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
public class DomainToolController {
    private static final Set<String> FORBIDDEN_MODEL_IDENTIFIERS = Set.of(
            "contactId", "turnId", "idempotencyKey", "phoneNumber");
    private static final Pattern RUNTIME_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private final DomainToolInvocationUseCase invocation;
    private final String expectedToken;
    private final String expectedPrincipal;

    public DomainToolController(DomainToolInvocationUseCase invocation, String expectedToken) {
        this(invocation, expectedToken, "hermes-urbana-domain");
    }

    @Autowired
    public DomainToolController(DomainToolInvocationUseCase invocation,
                                @Value("${hermes.sessions.internal-tool-token:}") String expectedToken,
                                @Value("${hermes.sessions.internal-tool-principal:hermes-urbana-domain}") String expectedPrincipal) {
        this.invocation = invocation;
        this.expectedToken = expectedToken == null ? "" : expectedToken;
        this.expectedPrincipal = expectedPrincipal == null || expectedPrincipal.isBlank()
                ? "hermes-urbana-domain" : expectedPrincipal;
    }

    @PostMapping("/{toolName}")
    public ResponseEntity<ToolResponse> invoke(
            @PathVariable String toolName,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody(required = false) ToolRequest request) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ToolResponse.error("invalid internal tool token"));
        }
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()
                || request.principal() == null || request.principal().isBlank()) {
            return ResponseEntity.badRequest().body(ToolResponse.error("sessionId and principal are required"));
        }
        if (!RUNTIME_ID.matcher(request.sessionId()).matches()
                || request.principal().length() > 128) {
            return ResponseEntity.badRequest().body(ToolResponse.error("runtime identity is invalid"));
        }
        if (!expectedPrincipal.equals(request.principal())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ToolResponse.error("technical principal is not allowlisted"));
        }
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        if (arguments.size() > 32 || arguments.toString().length() > 8192) {
            return ResponseEntity.badRequest().body(ToolResponse.error("tool arguments exceed the POC limit"));
        }
        if (FORBIDDEN_MODEL_IDENTIFIERS.stream().anyMatch(arguments::containsKey)) {
            return ResponseEntity.badRequest().body(ToolResponse.error("model identifiers are not accepted"));
        }
        final DomainToolName name;
        try {
            name = DomainToolName.fromWireName(toolName);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ToolResponse.error("tool is not allowlisted"));
        }
        try {
            var result = invocation.invoke(request.sessionId(), request.principal(), name, arguments);
            return ResponseEntity.ok(ToolResponse.success(result.result(), result.idempotencyKey()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ToolResponse.error(safeError(exception.getMessage())));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ToolResponse.error("domain operation failed"));
        }
    }

    private boolean isAuthorized(String authorization) {
        return expectedToken != null && !expectedToken.isBlank()
                && authorization != null && authorization.equals("Bearer " + expectedToken);
    }

    private String safeError(String message) {
        String value = message == null || message.isBlank() ? "domain operation rejected" : message;
        String redacted = value.replaceAll(
                "(?i)(openrouter_api_key|hermes_api_server_key|hermes_internal_tool_token|password|secret|token)"
                        + "(\\s*[:=]\\s*)[^\\s,;]+",
                "[REDACTED]");
        if (expectedToken != null && !expectedToken.isBlank()) {
            redacted = redacted.replace(expectedToken, "[REDACTED]");
        }
        return redacted.length() > 240 ? redacted.substring(0, 240) + "…" : redacted;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ToolRequest(String sessionId, String principal, Map<String, Object> arguments) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolResponse(boolean ok, Object result, String error, String idempotencyKey) {
        static ToolResponse success(Object result, String idempotencyKey) {
            return new ToolResponse(true, result, null, idempotencyKey);
        }

        static ToolResponse error(String error) {
            return new ToolResponse(false, null, error == null ? "domain operation rejected" : error, null);
        }
    }
}
