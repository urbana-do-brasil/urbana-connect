package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReceptionFailureRecoveryTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void returnsRetryableWithoutOutboundAndRetriesTheSameEventIdAfterTransientHermesFailure() {
        HermesSessionsGateway sessions = mock(HermesSessionsGateway.class);
        when(sessions.createSession("poc:ana")).thenReturn("session-1");
        AtomicBoolean firstCall = new AtomicBoolean(true);
        when(sessions.chat(eq("session-1"), any())).thenAnswer(invocation -> {
            if (firstCall.getAndSet(false)) {
                throw new IllegalStateException("temporary timeout");
            }
            return new HermesSessionsGateway.HermesChatResult(
                    "session-1", "session-1", "{\"message\":\"recovered\",\"nextAction\":\"AWAIT_CUSTOMER\"}",
                    AgentUsage.empty(), Map.of());
        });
        AgentSessionLinkGateway links = mock(AgentSessionLinkGateway.class);
        when(links.findActiveByContactId("poc:ana")).thenReturn(Optional.empty());
        when(links.createIfAbsent(any(AgentSessionLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceptionConversationGateway conversations = mock(ReceptionConversationGateway.class);
        AtomicReference<br.com.urbana.connect.domain.reception.model.ReceptionConversation> storedConversation =
                new AtomicReference<>();
        when(conversations.findByContactId("poc:ana")).thenAnswer(invocation ->
                Optional.ofNullable(storedConversation.get()));
        when(conversations.save(any())).thenAnswer(invocation -> {
            var conversation = invocation.getArgument(0,
                    br.com.urbana.connect.domain.reception.model.ReceptionConversation.class);
            storedConversation.set(conversation);
            return conversation;
        });
        ReceptionTranscriptGateway transcript = mock(ReceptionTranscriptGateway.class);
        Map<String, ReceptionMessage> storedMessages = new HashMap<>();
        when(transcript.findByEventId(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(storedMessages.get(invocation.getArgument(0))));
        when(transcript.appendIfAbsent(any(ReceptionMessage.class))).thenAnswer(invocation -> {
            ReceptionMessage message = invocation.getArgument(0);
            return storedMessages.putIfAbsent(message.eventId(), message) == null;
        });
        ReceptionTurnGateway turns = mock(ReceptionTurnGateway.class);
        when(turns.save(any(ReceptionTurn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, ReceptionTurn> storedTurns = new HashMap<>();
        when(turns.save(any(ReceptionTurn.class))).thenAnswer(invocation -> {
            ReceptionTurn turn = invocation.getArgument(0);
            storedTurns.put(turn.id(), turn);
            return turn;
        });
        when(turns.findByInboundMessageId(anyString())).thenAnswer(invocation -> storedTurns.values().stream()
                .filter(turn -> turn.inboundMessageIds().contains(invocation.getArgument(0)))
                .findFirst());

        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, links, Clock.fixed(NOW, ZoneOffset.UTC)),
                conversations, mock(CustomerFactGateway.class), transcript, turns,
                new CommercialPolicyService(), new ReceptionTurnCoordinator(), Clock.fixed(NOW, ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(
                new InboundConversationEvent("failure-1", "poc:ana",
                        br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                        "Oi", NOW));

        assertThat(receipt.status().name()).isEqualTo("FAILED_RETRYABLE");
        assertThat(receipt.output()).isNull();
        ArgumentCaptor<ReceptionMessage> capturedMessages = ArgumentCaptor.forClass(ReceptionMessage.class);
        verify(transcript, times(1)).appendIfAbsent(capturedMessages.capture());
        assertThat(capturedMessages.getAllValues()).extracting(ReceptionMessage::direction)
                .containsExactly(ReceptionMessageDirection.INBOUND);
        ArgumentCaptor<ReceptionTurn> capturedTurns = ArgumentCaptor.forClass(ReceptionTurn.class);
        verify(turns, times(2)).save(capturedTurns.capture());
        assertThat(capturedTurns.getAllValues().getLast().status()).isEqualTo(ReceptionTurnStatus.FAILED);
        assertThat(capturedTurns.getAllValues().getLast().failureCode()).isEqualTo("FAILED_RETRYABLE");

        ReceptionOrchestrator.TurnReceipt retry = orchestrator.process(
                new InboundConversationEvent("failure-1", "poc:ana",
                        br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                        "Oi", NOW));

        assertThat(retry.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(retry.output().message()).isEqualTo(
                "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. recovered");
        verify(sessions, times(2)).chat(eq("session-1"), any());
        ArgumentCaptor<ReceptionMessage> allMessages = ArgumentCaptor.forClass(ReceptionMessage.class);
        verify(transcript, times(2)).appendIfAbsent(allMessages.capture());
        assertThat(allMessages.getAllValues()).extracting(ReceptionMessage::direction)
                .containsExactly(ReceptionMessageDirection.INBOUND, ReceptionMessageDirection.OUTBOUND);

        ReceptionOrchestrator.TurnReceipt duplicate = orchestrator.process(
                new InboundConversationEvent("failure-1", "poc:ana",
                        br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                        "Oi", NOW));

        assertThat(duplicate.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.DUPLICATE);
        verify(sessions, times(2)).chat(eq("session-1"), any());
    }

    @Test
    void replacesOnlyTheLostHermesSessionBeforeRetryingTheTurn() {
        RecoverySessions sessions = new RecoverySessions();
        RecoveryLinks links = new RecoveryLinks();
        HermesSessionService service = new HermesSessionService(sessions, links, Clock.fixed(NOW, ZoneOffset.UTC));

        service.resolve("poc:ana");
        sessions.throwNotFound = true;
        HermesSessionsGateway.HermesChatResult result = service.chat("poc:ana",
                new HermesSessionsGateway.HermesChatRequest("retomar", List.of(), null, null, null));

        assertThat(result.content()).isEqualTo("recovered");
        assertThat(sessions.createdContacts).containsExactly("poc:ana", "poc:ana");
        assertThat(links.active.hermesSessionId()).isEqualTo("session-2");
        assertThat(links.lost).isTrue();
    }

    private static final class RecoverySessions implements HermesSessionsGateway {
        private final List<String> createdContacts = new ArrayList<>();
        private boolean throwNotFound;
        private int sequence;

        @Override
        public String createSession(String contactId) {
            createdContacts.add(contactId);
            sequence++;
            return "session-" + sequence;
        }

        @Override
        public HermesChatResult chat(String sessionId, HermesChatRequest request) {
            if (throwNotFound) {
                throwNotFound = false;
                throw HttpHermesSessionsGateway.HermesSessionsException.fromStatus(404, "session missing");
            }
            return new HermesChatResult(sessionId, sessionId, "recovered", AgentUsage.empty(), Map.of());
        }

        @Override
        public List<HermesHistoryMessage> history(String sessionId) {
            return List.of();
        }
    }

    private static final class RecoveryLinks implements AgentSessionLinkGateway {
        private AgentSessionLink active;
        private boolean lost;

        @Override
        public Optional<AgentSessionLink> findActiveByContactId(String contactId) {
            return Optional.ofNullable(active);
        }

        @Override
        public Optional<AgentSessionLink> findBySessionId(String sessionId) {
            return Optional.empty();
        }

        @Override
        public AgentSessionLink createIfAbsent(AgentSessionLink link) {
            if (active == null) active = link;
            return active;
        }

        @Override
        public AgentSessionLink touchActive(String contactId, String expectedSessionId, Instant lastUsedAt) {
            active = active.touch(lastUsedAt);
            return active;
        }

        @Override
        public AgentSessionLink replaceActive(String contactId, String expectedSessionId,
                                               AgentSessionLink replacement,
                                               br.com.urbana.connect.domain.reception.model.SessionLinkStatus previousStatus) {
            lost = previousStatus == br.com.urbana.connect.domain.reception.model.SessionLinkStatus.LOST;
            active = replacement;
            return replacement;
        }
    }
}
