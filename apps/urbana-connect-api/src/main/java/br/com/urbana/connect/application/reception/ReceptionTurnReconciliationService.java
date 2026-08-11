package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionEventIds;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.infrastructure.hermes.HermesAgentOutputParser;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Completes an uncertain turn only when Hermes exposes a changed stable cursor. */
public final class ReceptionTurnReconciliationService {
    private final HermesSessionService hermes;
    private final ReceptionConversationGateway conversations;
    private final ReceptionTranscriptGateway transcript;
    private final ReceptionTurnGateway turns;
    private final Clock clock;
    private final ActiveTurnLeaseService leases;
    private final HermesAgentOutputParser parser = new HermesAgentOutputParser();

    public ReceptionTurnReconciliationService(HermesSessionService hermes,
                                              ReceptionConversationGateway conversations,
                                              ReceptionTranscriptGateway transcript,
                                              ReceptionTurnGateway turns,
                                              Clock clock) {
        this(hermes, conversations, transcript, turns, clock, null);
    }

    public ReceptionTurnReconciliationService(HermesSessionService hermes,
                                              ReceptionConversationGateway conversations,
                                              ReceptionTranscriptGateway transcript,
                                              ReceptionTurnGateway turns,
                                              Clock clock,
                                              ActiveTurnLeaseService leases) {
        this.hermes = Objects.requireNonNull(hermes, "hermes");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.turns = Objects.requireNonNull(turns, "turns");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.leases = leases;
    }

    public Optional<String> reconcile(String turnOrEventId) {
        ReceptionTurn turn = turns.findById(turnOrEventId).orElseGet(() ->
                transcript.findByEventId(turnOrEventId)
                        .flatMap(message -> turns.findByInboundMessageId(message.id())).orElse(null));
        if (turn == null || turn.status() != ReceptionTurnStatus.RECONCILING) return Optional.empty();

        Checkpoint checkpoint = Checkpoint.parse(turn.historyCheckpoint()).orElse(null);
        if (checkpoint == null) return Optional.empty();
        HermesSessionsGateway.HermesHistorySnapshot snapshot = hermes.historySnapshot(turn.hermesSessionId());
        String cursor = snapshot.stableCursor().orElse(null);
        if (cursor == null || cursor.equals(checkpoint.cursor()) || snapshot.messages().size() <= checkpoint.size()) {
            return Optional.empty();
        }

        List<HermesSessionsGateway.HermesHistoryMessage> newMessages = snapshot.messages()
                .subList(Math.min(checkpoint.size(), snapshot.messages().size()), snapshot.messages().size());
        AgentOutput output = newMessages.stream()
                .filter(message -> "assistant".equalsIgnoreCase(message.role()))
                .map(message -> parseSafely(message.content()))
                .filter(Objects::nonNull)
                .reduce((first, last) -> last)
                .orElse(null);
        if (output == null) return Optional.empty();

        ReceptionConversation conversation = conversations.findByContactId(turn.contactId()).orElse(null);
        if (conversation == null) return Optional.empty();
        String eventId = ReceptionEventIds.outbound(turn.id(), turn.correlationId());
        ReceptionMessage outbound = new ReceptionMessage(UUID.randomUUID().toString(), eventId,
                turn.correlationId(), conversation.id(), turn.contactId(), ReceptionMessageDirection.OUTBOUND,
                ReceptionMessageSender.URBA, ReceptionMessageType.TEXT, output.message(), null, null, clock.instant());
        transcript.appendIfAbsent(outbound);
        ReceptionTurn completed = turn.complete(turn.usage(), clock.instant(), output);
        turns.save(completed);
        if (leases != null) {
            leases.releaseForReconciliation(turn.hermesSessionId(), turn.id());
        }
        return Optional.of(output.message());
    }

    private AgentOutput parseSafely(String content) {
        try {
            return parser.parse(content);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private record Checkpoint(String cursor, int size) {
        private static Optional<Checkpoint> parse(String value) {
            if (value == null || value.isBlank()) return Optional.empty();
            int separator = value.lastIndexOf('|');
            if (separator <= 0 || separator == value.length() - 1) return Optional.empty();
            try {
                int size = Integer.parseInt(value.substring(separator + 1));
                return size < 0 ? Optional.empty() : Optional.of(new Checkpoint(value.substring(0, separator), size));
            } catch (NumberFormatException invalid) {
                return Optional.empty();
            }
        }
    }
}
