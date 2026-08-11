package br.com.urbana.connect.infrastructure.hermes;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Parses Hermes conversation text while keeping strict validation for legacy operational envelopes. */
public final class HermesAgentOutputParser {
    private final ObjectMapper mapper;

    public HermesAgentOutputParser() {
        this(new ObjectMapper());
    }

    public HermesAgentOutputParser(ObjectMapper mapper) {
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    /**
     * Parses the normal conversational response. Plain text is authoritative and
     * is returned byte-for-byte as the AgentOutput message. A legacy object that
     * contains a textual message is tolerated only to keep old Hermes sessions
     * readable; its operational fields do not participate in conversation.
     */
    public AgentOutput parse(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidAgentOutputException("agent output is empty");
        }
        try {
            JsonNode root = mapper.readTree(content);
            if (root != null && root.isObject()) {
                JsonNode message = root.get("message");
                if (message != null && message.isTextual()) {
                    return new AgentOutput(message.asText(), AgentNextAction.NONE);
                }
            }
        } catch (JsonProcessingException ignored) {
            // The normal contract is text. Invalid JSON is therefore still a
            // valid conversational string and must not become a synthetic error.
        }
        return new AgentOutput(content, AgentNextAction.NONE);
    }

    /**
     * Keeps the old strict envelope contract available only to operational
     * consumers that explicitly opt into it.
     */
    public AgentOutput parseOperationalEnvelope(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidAgentOutputException("agent output is empty");
        }
        try {
            JsonNode root = mapper.readTree(content);
            if (root == null || !root.isObject()) {
                throw new InvalidAgentOutputException("agent output must be a JSON object");
            }
            var fields = root.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!field.equals("message") && !field.equals("nextAction") && !field.equals("handoffReason")) {
                    throw new InvalidAgentOutputException("unknown output field: " + field);
                }
            }
            JsonNode message = root.get("message");
            JsonNode action = root.get("nextAction");
            if (message == null || !message.isTextual() || message.asText().isBlank()) {
                throw new InvalidAgentOutputException("message must be a non-empty string");
            }
            if (message.asText().length() > 4096) {
                throw new InvalidAgentOutputException("message is too long");
            }
            if (action == null || !action.isTextual()) {
                throw new InvalidAgentOutputException("nextAction must be a string");
            }
            AgentNextAction nextAction;
            try {
                nextAction = AgentNextAction.valueOf(action.asText());
            } catch (IllegalArgumentException exception) {
                throw new InvalidAgentOutputException("unknown nextAction: " + action.asText(), exception);
            }
            JsonNode reasonNode = root.get("handoffReason");
            String reason = reasonNode == null || reasonNode.isNull() ? null : reasonNode.isTextual()
                    ? reasonNode.asText() : null;
            if (reasonNode != null && !reasonNode.isNull() && !reasonNode.isTextual()) {
                throw new InvalidAgentOutputException("handoffReason must be a string or null");
            }
            return new AgentOutput(message.asText(), nextAction, reason);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            if (exception instanceof InvalidAgentOutputException invalid) {
                throw invalid;
            }
            throw new InvalidAgentOutputException("invalid agent JSON: " + exception.getMessage(), exception);
        }
    }

    public static final class InvalidAgentOutputException extends IllegalArgumentException {
        public InvalidAgentOutputException(String message) {
            super(message);
        }

        public InvalidAgentOutputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
