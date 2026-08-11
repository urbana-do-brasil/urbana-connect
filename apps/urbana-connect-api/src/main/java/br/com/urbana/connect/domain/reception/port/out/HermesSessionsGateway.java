package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.AgentUsage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port for the native Hermes Sessions API. */
public interface HermesSessionsGateway {

    String createSession(String contactId);

    HermesChatResult chat(String sessionId, HermesChatRequest request);

    List<HermesHistoryMessage> history(String sessionId);

    /** Reconciliation only trusts a stable cursor; absent cursor means fail-closed. */
    default HermesHistorySnapshot historySnapshot(String sessionId) {
        return new HermesHistorySnapshot(null, history(sessionId));
    }

    default String createSession() {
        return createSession("anonymous");
    }

    record HermesChatRequest(String input, List<String> images, String model,
                             String provider, String reasoningEffort) {
        public HermesChatRequest {
            images = images == null ? List.of() : List.copyOf(images);
            if ((input == null || input.isBlank()) && images.isEmpty()) {
                throw new IllegalArgumentException("input or image is required");
            }
            input = input == null ? "" : input;
            model = model == null || model.isBlank() ? "openai/gpt-5.6-luna" : model;
            provider = provider == null || provider.isBlank() ? "openrouter" : provider;
            reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank() ? "max" : reasoningEffort;
        }

        public HermesChatRequest(String input) {
            this(input, List.of(), "openai/gpt-5.6-luna", "openrouter", "max");
        }
    }

    record HermesChatResult(String requestedSessionId, String effectiveSessionId,
                            String content, AgentUsage usage, Map<String, Object> raw) {
        public HermesChatResult {
            if (requestedSessionId == null || requestedSessionId.isBlank()
                    || effectiveSessionId == null || effectiveSessionId.isBlank()) {
                throw new IllegalArgumentException("session ids are required");
            }
            if (content == null) {
                throw new IllegalArgumentException("content is required");
            }
            usage = usage == null ? AgentUsage.empty() : usage;
            raw = raw == null ? Map.of() : Map.copyOf(raw);
        }
    }

    record HermesHistoryMessage(String role, String content) {
        public HermesHistoryMessage {
            if (role == null || role.isBlank() || content == null) {
                throw new IllegalArgumentException("history message is incomplete");
            }
        }
    }

    record HermesHistorySnapshot(String cursor, List<HermesHistoryMessage> messages) {
        public HermesHistorySnapshot {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        public Optional<String> stableCursor() {
            return cursor == null || cursor.isBlank() ? Optional.empty() : Optional.of(cursor);
        }
    }
}
