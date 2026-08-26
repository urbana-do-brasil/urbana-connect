package br.com.urbana.connect.application.reception.tools;

import br.com.urbana.connect.application.reception.ActiveTurnLeaseService;
import br.com.urbana.connect.application.reception.ReceptionMetrics;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocationStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Resolves lease identity and durably replays idempotent tool outcomes. */
public class DomainToolInvocationUseCase {
    private static final ObjectMapper CANONICAL_JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ActiveTurnLeaseService leases;
    private final DomainToolInvocationGateway invocations;
    private final DomainToolService tools;
    private final Clock clock;
    private final ReceptionConversationGateway conversations;
    private final ReceptionMetrics metrics;

    public DomainToolInvocationUseCase(ActiveTurnLeaseService leases, DomainToolInvocationGateway invocations,
                                       DomainToolService tools) {
        this(leases, invocations, tools, Clock.systemUTC());
    }

    public DomainToolInvocationUseCase(ActiveTurnLeaseService leases, DomainToolInvocationGateway invocations,
                                       DomainToolService tools, Clock clock) {
        this(leases, invocations, tools, clock, null);
    }

    public DomainToolInvocationUseCase(ActiveTurnLeaseService leases, DomainToolInvocationGateway invocations,
                                       DomainToolService tools, Clock clock,
                                       ReceptionConversationGateway conversations) {
        this(leases, invocations, tools, clock, conversations, null);
    }

    public DomainToolInvocationUseCase(ActiveTurnLeaseService leases, DomainToolInvocationGateway invocations,
                                       DomainToolService tools, Clock clock,
                                       ReceptionConversationGateway conversations, ReceptionMetrics metrics) {
        this.leases = leases;
        this.invocations = invocations;
        this.tools = tools;
        this.clock = clock;
        this.conversations = conversations;
        this.metrics = metrics;
    }

    public InvocationResult invoke(String sessionId, String principal, DomainToolName toolName,
                                   Map<String, Object> arguments) {
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("technical principal is required");
        }
        if (toolName == null) {
            throw new IllegalArgumentException("tool is required");
        }
        Map<String, Object> normalized = arguments == null ? Map.of() : new TreeMap<>(arguments);
        String hash = hash(normalized);
        var lease = leases.requireActive(sessionId);
        if (conversations != null) {
            ReceptionConversation conversation = conversations.findByContactId(lease.contactId())
                    .orElseThrow(() -> new IllegalStateException("conversation does not exist"));
            if (conversation.mode() == ReceptionMode.HUMAN
                    && toolName != DomainToolName.REQUEST_HUMAN_HANDOFF) {
                throw new DomainRejectionException("HUMAN_OWNS_CONVERSATION", "WAIT_FOR_HUMAN",
                        List.of(), "A arquiteta continuará este atendimento por aqui.");
            }
        }
        String key = DomainToolInvocation.deriveIdempotencyKey(lease.turnId(), toolName, hash);

        var prior = invocations.findByIdempotencyKey(key);
        if (prior.isPresent()) {
            return replayOrReject(prior.get(), key);
        }

        Instant now = clock.instant();
        DomainToolInvocation started = new DomainToolInvocation(UUID.randomUUID().toString(), key,
                lease.turnId(), lease.hermesSessionId(), lease.contactId(), toolName, hash,
                DomainToolInvocationStatus.STARTED, null, null, now, null);
        try {
            invocations.save(started);
        } catch (DuplicateKeyException race) {
            var concurrent = invocations.findByIdempotencyKey(key)
                    .orElseThrow(() -> race);
            return replayOrReject(concurrent, key);
        }
        if (metrics != null) {
            metrics.recordToolInvocation(key);
        }

        try {
            Map<String, Object> result = tools.execute(toolName, lease.contactId(), normalized,
                    new ToolExecutionContext(lease, clock.instant()));
            Object payloadSnapshot = snapshot(result);
            invocations.save(started.finish(DomainToolInvocationStatus.SUCCEEDED, "OK", payloadSnapshot,
                    clock.instant()));
            return new InvocationResult(snapshot(payloadSnapshot), key, false);
        } catch (DomainRejectionException rejection) {
            Object failurePayload = snapshot(rejection.toPayload());
            invocations.save(started.finish(DomainToolInvocationStatus.REJECTED,
                    rejection.code(), failurePayload, clock.instant()));
            throw rejection;
        } catch (RuntimeException exception) {
            Object failurePayload = snapshot(Map.of(
                    "code", "TEMPORARILY_UNAVAILABLE",
                    "nextAction", "WAIT_OR_HANDOFF",
                    "missingFields", List.of(),
                    "customerMessage", "Não consegui concluir esta etapa agora. Posso tentar novamente ou chamar a arquiteta."));
            invocations.save(started.finish(DomainToolInvocationStatus.FAILED,
                    "TECHNICAL_FAILURE", failurePayload, clock.instant()));
            throw new TechnicalToolFailureException("Não consegui concluir esta etapa agora.", exception);
        }
    }

    private InvocationResult replayOrReject(DomainToolInvocation prior, String key) {
        return switch (prior.status()) {
            case SUCCEEDED -> new InvocationResult(snapshot(prior.resultPayload()), key, true);
            case STARTED -> throw new InvocationInProgressException(
                    "tool invocation is already in progress");
            case REJECTED -> throw DomainRejectionException.from(prior.resultCode(), prior.resultPayload());
            case FAILED -> throw new DurableToolFailureException(
                    "Não consegui concluir esta etapa agora.", prior.resultCode(), prior.resultPayload());
        };
    }

    private static String hash(Map<String, Object> normalized) {
        try {
            String canonical = CANONICAL_JSON.writeValueAsString(normalized);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("unable to derive canonical tool arguments hash", exception);
        }
    }

    public record InvocationResult(Object result, String idempotencyKey, boolean duplicate) { }

    public static class InvocationInProgressException extends IllegalArgumentException {
        public InvocationInProgressException(String message) {
            super("Esta etapa ainda está sendo concluída.");
        }
    }

    public static final class DomainRejectionException extends IllegalArgumentException {
        private final String code;
        private final String nextAction;
        private final List<String> missingFields;
        private final String customerMessage;

        public DomainRejectionException(String code, String nextAction,
                                        List<String> missingFields, String customerMessage) {
            super(customerMessage);
            this.code = requireText(code, "BUSINESS_RULE_REJECTED");
            this.nextAction = requireText(nextAction, "ASK_FOR_CLARIFICATION");
            this.missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
            this.customerMessage = requireText(customerMessage,
                    "Preciso confirmar uma informação antes de continuar.");
        }

        public String code() { return code; }
        public String nextAction() { return nextAction; }
        public List<String> missingFields() { return missingFields; }
        public String customerMessage() { return customerMessage; }

        public Map<String, Object> toPayload() {
            return Map.of("code", code, "nextAction", nextAction,
                    "missingFields", missingFields, "customerMessage", customerMessage);
        }

        @SuppressWarnings("unchecked")
        static DomainRejectionException from(String code, Object payload) {
            if (payload instanceof Map<?, ?> map) {
                Object fields = map.get("missingFields");
                List<String> missing = fields instanceof List<?> list
                        ? list.stream().map(String::valueOf).toList() : List.of();
                Object replayCode = map.containsKey("code") ? map.get("code") : code;
                Object replayAction = map.containsKey("nextAction") ? map.get("nextAction") : "ASK_FOR_CLARIFICATION";
                Object replayMessage = map.containsKey("customerMessage") ? map.get("customerMessage")
                        : "Preciso confirmar uma informação antes de continuar.";
                return new DomainRejectionException(String.valueOf(replayCode),
                        String.valueOf(replayAction), missing, String.valueOf(replayMessage));
            }
            return new DomainRejectionException(code, "ASK_FOR_CLARIFICATION", List.of(),
                    "Preciso confirmar uma informação antes de continuar.");
        }

        private static String requireText(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    public static final class TechnicalToolFailureException extends IllegalStateException {
        TechnicalToolFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class DurableToolFailureException extends IllegalArgumentException {
        private final String resultCode;
        private final Object resultPayload;

        public DurableToolFailureException(String message, String resultCode, Object resultPayload) {
            super(message);
            this.resultCode = resultCode;
            this.resultPayload = resultPayload;
        }

        public String resultCode() {
            return resultCode;
        }

        public Object resultPayload() {
            return resultPayload;
        }
    }

    private static Object snapshot(Object payload) {
        try {
            // Round-tripping through JSON gives the ledger a detached, JSON-safe
            // value.  A caller mutating the domain result after return cannot
            // alter the durable outcome that a retry will replay.
            return CANONICAL_JSON.readValue(CANONICAL_JSON.writeValueAsBytes(payload), Object.class);
        } catch (Exception exception) {
            throw new IllegalStateException("unable to snapshot tool result", exception);
        }
    }
}
