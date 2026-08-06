package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReturningCustomerOrchestratorTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void addsOnlyConfirmedContactScopedFactsToTheNextHermesTurn() {
        ReceptionConversationGateway conversations = mock(ReceptionConversationGateway.class);
        when(conversations.findByContactId("poc:ana")).thenReturn(Optional.empty());
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerFactGateway facts = mock(CustomerFactGateway.class);
        List<CustomerFact> factsInStore = List.of(
                CustomerFact.confirmed("poc:ana", "OCCUPATION", "DESIGNER", "m-1", NOW),
                CustomerFact.confirmed("poc:ana", "SELECTED_SERVICE", "DECOR", "m-2", NOW),
                CustomerFact.confirmed("poc:sentinel", "OCCUPATION", "ENGENHEIRO", "m-3", NOW));
        when(facts.findCurrentByContactId("poc:ana", NOW)).thenReturn(factsInStore);
        when(facts.findByContactId("poc:ana")).thenReturn(factsInStore);

        ReceptionTranscriptGateway transcript = mock(ReceptionTranscriptGateway.class);
        when(transcript.findByEventId("returning-1")).thenReturn(Optional.empty());
        when(transcript.appendIfAbsent(any(ReceptionMessage.class))).thenReturn(true);
        ReceptionTurnGateway turns = mock(ReceptionTurnGateway.class);
        when(turns.save(any(ReceptionTurn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentSessionLinkGateway links = mock(AgentSessionLinkGateway.class);
        when(links.findActiveByContactId("poc:ana")).thenReturn(Optional.empty());
        when(links.createIfAbsent(any(AgentSessionLink.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CapturingSessions sessions = new CapturingSessions();
        CommercialPolicyService policy = new CommercialPolicyService();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, links, clock), conversations, facts, transcript, turns,
                policy, new ReceptionTurnCoordinator(), null, null, clock, new ReceptionMetrics(),
                new ReturningCustomerService(facts, policy, clock));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "returning-1", "poc:ana", ReceptionMessageType.TEXT,
                "Podemos continuar?", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(sessions.lastRequest.input()).contains("OCCUPATION=DESIGNER", "SELECTED_SERVICE=DECOR")
                .doesNotContain("ENGENHEIRO", "poc:sentinel").contains("Podemos continuar?");
    }

    private static final class CapturingSessions implements HermesSessionsGateway {
        private HermesChatRequest lastRequest;

        @Override
        public String createSession(String contactId) {
            return "session-returning";
        }

        @Override
        public HermesChatResult chat(String sessionId, HermesChatRequest request) {
            lastRequest = request;
            return new HermesChatResult(sessionId, sessionId,
                    "{\"message\":\"Vamos continuar.\",\"nextAction\":\"AWAIT_CUSTOMER\"}",
                    AgentUsage.empty(), Map.of());
        }

        @Override
        public List<HermesHistoryMessage> history(String sessionId) {
            return List.of();
        }
    }
}
