package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway.HermesHistorySnapshot;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReceptionTurnReconciliationTest {
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");

    @Test
    void persistsOneCanonicalOutputWhenAResponseAppearsAfterTheCheckpoint() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn turn = ReceptionTurn.queued("turn-1", "corr-1", "poc:ana", "session-1",
                List.of("message-1"), NOW, "cursor-1|1")
                .start(NOW.plusSeconds(1))
                .reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", NOW.plusSeconds(2));
        turns.save(turn);
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("message-1", "event-1", "corr-1", "conversation-1",
                "poc:ana", ReceptionMessageDirection.INBOUND, ReceptionMessageSender.CONTACT,
                ReceptionMessageType.TEXT, "Oi", null, null, NOW));

        HermesSessionsGateway sessions = new HermesSessionsGateway() {
            @Override public String createSession(String contactId) { return "session-1"; }
            @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) { throw new AssertionError("must not dispatch"); }
            @Override public List<HermesHistoryMessage> history(String sessionId) { return List.of(); }
            @Override public HermesHistorySnapshot historySnapshot(String sessionId) {
                return new HermesHistorySnapshot("cursor-2", List.of(
                        new HermesHistoryMessage("user", "Oi"),
                        new HermesHistoryMessage("assistant", "{\"message\":\"resposta tardia\",\"nextAction\":\"AWAIT_CUSTOMER\"}")));
            }
        };
        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(sessions, new EmptyLinks()), conversations, transcript, turns,
                Clock.fixed(NOW.plusSeconds(3), ZoneOffset.UTC));

        assertThat(service.reconcile("turn-1")).contains("resposta tardia");
        assertThat(service.reconcile("turn-1")).isEmpty();
        assertThat(transcript.messages).filteredOn(message -> message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text).isEqualTo("resposta tardia");
        assertThat(turns.value.status().name()).isEqualTo("COMPLETED");
    }

    @Test
    void remainsFailClosedWhenHermesDoesNotReturnAStableHistoryCursor() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn turn = ReceptionTurn.queued("turn-2", "corr-2", "poc:ana", "session-1",
                List.of("message-2"), NOW, null).start(NOW).reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", NOW);
        turns.save(turn);

        HermesSessionsGateway sessions = new HermesSessionsGateway() {
            @Override public String createSession(String contactId) { return "session-1"; }
            @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) { throw new AssertionError("must not dispatch"); }
            @Override public List<HermesHistoryMessage> history(String sessionId) {
                return List.of(new HermesHistoryMessage("assistant", "invented?"));
            }
        };
        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(sessions, new EmptyLinks()), new MemoryConversation(), new MemoryTranscript(), turns,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.reconcile("turn-2")).isEmpty();
        assertThat(turns.value.status().name()).isEqualTo("RECONCILING");
    }

    @Test
    void releasesTheHeldLeaseOnlyAfterPersistingTheCanonicalOutput() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn turn = ReceptionTurn.queued("turn-3", "corr-3", "poc:ana", "session-1",
                List.of("message-3"), NOW, "cursor-1|1")
                .start(NOW)
                .reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", NOW);
        turns.save(turn);
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-3", "poc:ana", NOW);
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("message-3", "event-3", "corr-3", "conversation-3",
                "poc:ana", ReceptionMessageDirection.INBOUND, ReceptionMessageSender.CONTACT,
                ReceptionMessageType.TEXT, "Oi", null, null, NOW));
        HermesSessionsGateway sessions = new HermesSessionsGateway() {
            @Override public String createSession(String contactId) { return "session-1"; }
            @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) {
                throw new AssertionError("must not dispatch");
            }
            @Override public List<HermesHistoryMessage> history(String sessionId) { return List.of(); }
            @Override public HermesHistorySnapshot historySnapshot(String sessionId) {
                return new HermesHistorySnapshot("cursor-2", List.of(
                        new HermesHistoryMessage("user", "Oi"),
                        new HermesHistoryMessage("assistant",
                                "{\"message\":\"resposta tardia\",\"nextAction\":\"AWAIT_CUSTOMER\"}")));
            }
        };
        ActiveTurnLeaseGateway gateway = mock(ActiveTurnLeaseGateway.class);
        ActiveTurnLeaseService leases = new ActiveTurnLeaseService(gateway,
                Clock.fixed(NOW, ZoneOffset.UTC), java.time.Duration.ofSeconds(30));
        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(sessions, new EmptyLinks()), conversations, transcript, turns,
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC), leases);

        assertThat(service.reconcile("turn-3")).contains("resposta tardia");
        verify(gateway).revoke(eq("session-1"), eq("turn-3"), any(Instant.class));
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND).hasSize(1);
    }

    private static final class EmptyLinks implements AgentSessionLinkGateway {
        @Override public Optional<br.com.urbana.connect.domain.reception.model.AgentSessionLink> findActiveByContactId(String contactId) { return Optional.empty(); }
        @Override public Optional<br.com.urbana.connect.domain.reception.model.AgentSessionLink> findBySessionId(String sessionId) { return Optional.empty(); }
        @Override public br.com.urbana.connect.domain.reception.model.AgentSessionLink createIfAbsent(br.com.urbana.connect.domain.reception.model.AgentSessionLink link) { return link; }
        @Override public br.com.urbana.connect.domain.reception.model.AgentSessionLink touchActive(String contactId, String expectedSessionId, Instant lastUsedAt) { throw new UnsupportedOperationException(); }
        @Override public br.com.urbana.connect.domain.reception.model.AgentSessionLink replaceActive(String contactId, String expectedSessionId, br.com.urbana.connect.domain.reception.model.AgentSessionLink replacement, br.com.urbana.connect.domain.reception.model.SessionLinkStatus previousStatus) { return replacement; }
    }

    private static final class MemoryConversation implements ReceptionConversationGateway {
        ReceptionConversation value;
        @Override public Optional<ReceptionConversation> findByContactId(String contactId) { return Optional.ofNullable(value); }
        @Override public ReceptionConversation save(ReceptionConversation conversation) { return value = conversation; }
    }

    private static final class MemoryTranscript implements ReceptionTranscriptGateway {
        final List<ReceptionMessage> messages = new ArrayList<>();
        @Override public boolean appendIfAbsent(ReceptionMessage message) {
            if (messages.stream().anyMatch(item -> item.eventId().equals(message.eventId()))) return false;
            messages.add(message); return true;
        }
        @Override public Optional<ReceptionMessage> findByEventId(String eventId) { return messages.stream().filter(item -> item.eventId().equals(eventId)).findFirst(); }
        @Override public List<ReceptionMessage> findByConversationId(String conversationId) { return List.copyOf(messages); }
    }

    private static final class MemoryTurns implements ReceptionTurnGateway {
        ReceptionTurn value;
        @Override public ReceptionTurn save(ReceptionTurn turn) { return value = turn; }
        @Override public Optional<ReceptionTurn> findById(String turnId) { return Optional.ofNullable(value); }
        @Override public Optional<ReceptionTurn> findByInboundMessageId(String messageId) { return Optional.ofNullable(value); }
    }
}
