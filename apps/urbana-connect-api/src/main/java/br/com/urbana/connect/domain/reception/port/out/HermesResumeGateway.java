package br.com.urbana.connect.domain.reception.port.out;

import java.util.List;
import java.util.Map;

/** PEE-103 internal capability. Synchronization is output-free and tool-free. */
public interface HermesResumeGateway {
    ContextSyncReceipt synchronize(String sessionId, ResumeContext context);

    ResumeDecision decide(String sessionId, ResumeCommand command);

    record ContextMessage(int sequence, String sourceMessageId, String senderType, String role, String content) { }

    record ContextFact(String type, String value, String confidence) { }

    record ResumeContext(int contractVersion, String resumeId, String lineageId, String idempotencyKey,
                         String mode, long cursor, int watermark, String checksum,
                         List<ContextMessage> messages, List<ContextFact> facts) {
        public ResumeContext(int contractVersion, String resumeId, String lineageId, String idempotencyKey,
                             String mode, long cursor, int watermark, String checksum,
                             List<ContextMessage> messages) {
            this(contractVersion, resumeId, lineageId, idempotencyKey, mode, cursor, watermark, checksum,
                    messages, List.of());
        }

        public ResumeContext {
            messages = messages == null ? List.of() : List.copyOf(messages);
            facts = facts == null ? List.of() : List.copyOf(facts);
        }
    }

    record ContextSyncReceipt(String resumeId, String lineageId, String effectiveSessionId,
                              String checksum, long cursor, int coveredThroughSequence) { }

    record ResumeCommand(int contractVersion, String resumeId, String lineageId, String idempotencyKey,
                         ContextSyncReceipt contextReceipt, Map<String, Object> directive) {
        public ResumeCommand {
            directive = directive == null ? Map.of() : Map.copyOf(directive);
        }
    }

    record ResumeDecision(String resumeId, String effectiveSessionId, Action action, String nextStep,
                          String message, List<String> evidenceMessageIds, String reasonCode,
                          double confidence) {
        public ResumeDecision {
            evidenceMessageIds = evidenceMessageIds == null ? List.of() : List.copyOf(evidenceMessageIds);
        }
    }

    enum Action { SEND_MESSAGE, WAIT, RETURN_TO_HUMAN }
}
