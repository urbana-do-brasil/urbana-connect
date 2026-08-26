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
import java.util.List;
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ToolResponse.error(
                    "ACCESS_DENIED", "STOP", "Não foi possível continuar esta solicitação."));
        }
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()
                || request.principal() == null || request.principal().isBlank()) {
            return ResponseEntity.badRequest().body(ToolResponse.error(
                    "INVALID_REQUEST", "CORRECT_INPUT", "Preciso de dados válidos para continuar."));
        }
        if (!RUNTIME_ID.matcher(request.sessionId()).matches()
                || request.principal().length() > 128) {
            return ResponseEntity.badRequest().body(ToolResponse.error(
                    "INVALID_REQUEST", "CORRECT_INPUT", "Preciso de dados válidos para continuar."));
        }
        if (!expectedPrincipal.equals(request.principal())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ToolResponse.error(
                    "ACCESS_DENIED", "STOP", "Não foi possível continuar esta solicitação."));
        }
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        if (arguments.size() > 32 || arguments.toString().length() > 8192) {
            return ResponseEntity.badRequest().body(ToolResponse.error(
                    "INVALID_REQUEST", "CORRECT_INPUT", "Preciso de dados válidos para continuar."));
        }
        if (FORBIDDEN_MODEL_IDENTIFIERS.stream().anyMatch(arguments::containsKey)) {
            return ResponseEntity.badRequest().body(ToolResponse.error(
                    "INVALID_REQUEST", "CORRECT_INPUT", "Preciso de dados válidos para continuar."));
        }
        final DomainToolName name;
        try {
            name = DomainToolName.fromWireName(toolName);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ToolResponse.error(
                    "ACTION_NOT_AVAILABLE", "CHOOSE_AVAILABLE_ACTION", "Essa ação não está disponível."));
        }
        try {
            var result = invocation.invoke(request.sessionId(), request.principal(), name, arguments);
            return ResponseEntity.ok(ToolResponse.success(result.result()));
        } catch (DomainToolInvocationUseCase.DomainRejectionException rejection) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ToolResponse.error(new ToolError(
                    rejection.code(), rejection.nextAction(), rejection.missingFields(), rejection.customerMessage())));
        } catch (DomainToolInvocationUseCase.InvocationInProgressException inProgress) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ToolResponse.error(
                    "ACTION_IN_PROGRESS", "WAIT", "Esta etapa ainda está sendo concluída."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ToolResponse.error(
                    "BUSINESS_RULE_REJECTED", "ASK_FOR_CLARIFICATION",
                    "Preciso confirmar uma informação antes de continuar."));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ToolResponse.error(
                    "TEMPORARILY_UNAVAILABLE", "WAIT_OR_HANDOFF",
                    "Não consegui concluir esta etapa agora. Posso tentar novamente ou chamar a arquiteta."));
        }
    }

    private boolean isAuthorized(String authorization) {
        return expectedToken != null && !expectedToken.isBlank()
                && authorization != null && authorization.equals("Bearer " + expectedToken);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ToolRequest(String sessionId, String principal, Map<String, Object> arguments) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolError(String code, String nextAction, List<String> missingFields, String customerMessage) {
        public ToolError {
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        }
    }

    public record ToolResponse(boolean ok, Object result, ToolError error, String idempotencyKey) {
        static ToolResponse success(Object result) {
            return new ToolResponse(true, result, null, null);
        }

        static ToolResponse error(String code, String nextAction, String customerMessage) {
            return error(new ToolError(code, nextAction, List.of(), customerMessage));
        }

        static ToolResponse error(ToolError error) {
            return new ToolResponse(false, null, error, null);
        }
    }
}
