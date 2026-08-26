package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.reception.tools.StatefulDomainToolService;
import br.com.urbana.connect.application.reception.tools.ToolExecutionContext;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocationStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HumanHandoffServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void humanModePersistsInboundPublishesTheSingleHandoffAckAndBlocksHermes() {
        HermesSessionService hermes = mock(HermesSessionService.class);
        ReceptionConversationGateway conversations = mock(ReceptionConversationGateway.class);
        CustomerFactGateway facts = mock(CustomerFactGateway.class);
        ReceptionTranscriptGateway transcript = mock(ReceptionTranscriptGateway.class);
        ReceptionTurnGateway turns = mock(ReceptionTurnGateway.class);
        ReceptionConversation human = ReceptionConversation.start("conversation-1", "poc:ana", NOW)
                .requestHumanHandoff("cliente pediu uma pessoa", NOW.plusSeconds(1));
        when(conversations.findByContactId("poc:ana")).thenReturn(Optional.of(human));
        when(transcript.findByEventId("event-human-1")).thenReturn(Optional.empty());
        when(transcript.appendIfAbsent(any(ReceptionMessage.class))).thenReturn(true);
        when(turns.save(any(ReceptionTurn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(hermes, conversations, facts, transcript,
                turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "event-human-1", "poc:ana", ReceptionMessageType.TEXT, "ainda preciso de ajuda", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.BLOCKED_BY_HUMAN);
        assertThat(receipt.output()).isNull();
        verifyNoInteractions(hermes);
        ArgumentCaptor<ReceptionMessage> messages = ArgumentCaptor.forClass(ReceptionMessage.class);
        verify(transcript, times(2)).appendIfAbsent(messages.capture());
        assertThat(messages.getAllValues()).extracting(ReceptionMessage::senderType)
                .containsExactly(ReceptionMessageSender.CONTACT, ReceptionMessageSender.URBA);
        assertThat(messages.getAllValues().get(1).text())
                .isEqualTo(StatefulDomainToolService.HUMAN_HANDOFF_ACK);
        ArgumentCaptor<ReceptionTurn> savedTurns = ArgumentCaptor.forClass(ReceptionTurn.class);
        verify(turns).save(savedTurns.capture());
        assertThat(savedTurns.getValue().status()).isEqualTo(ReceptionTurnStatus.BLOCKED_BY_HUMAN);
    }

    @Test
    void requestHumanHandoffToolTransitionsTheAuthoritativeConversationToHuman() {
        ReceptionConversationGateway conversations = mock(ReceptionConversationGateway.class);
        CustomerFactGateway facts = mock(CustomerFactGateway.class);
        ReceptionTranscriptGateway transcript = mock(ReceptionTranscriptGateway.class);
        ReceptionConversation ai = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
        when(conversations.findByContactId("poc:ana")).thenReturn(Optional.of(ai));
        when(conversations.save(any(ReceptionConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(), conversations,
                facts, transcript);
        ActiveTurnLease lease = new ActiveTurnLease("session-1", "turn-1", "poc:ana", "event-1",
                ActiveTurnLeaseStatus.RUNNING, NOW, NOW.plusSeconds(60), null, 0);

        Map<String, Object> result = tools.execute(DomainToolName.REQUEST_HUMAN_HANDOFF,
                "poc:ana", Map.of("reason", "não consigo resolver"), new ToolExecutionContext(lease, NOW));

        assertThat(result).containsEntry("status", "TRANSFERRED")
                .containsEntry("ownership", "HUMAN")
                .containsKey("handoffId");
        ArgumentCaptor<ReceptionConversation> saved = ArgumentCaptor.forClass(ReceptionConversation.class);
        verify(conversations).save(saved.capture());
        assertThat(saved.getValue().mode()).isEqualTo(ReceptionMode.HUMAN);
        assertThat(saved.getValue().handoffReason()).isEqualTo("não consigo resolver");
    }

    @Test
    void servicePersistsBackendOwnedHumanTransitionWithoutOfferingAnAiReturn() {
        ReceptionConversationGateway conversations = mock(ReceptionConversationGateway.class);
        ReceptionConversation ai = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
        when(conversations.findByContactId("poc:ana")).thenReturn(Optional.of(ai));
        when(conversations.save(any(ReceptionConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        HumanHandoffService service = new HumanHandoffService(conversations);

        ReceptionConversation result = service.enterHumanMode("poc:ana", "transferir para pessoa", NOW);

        assertThat(result.mode()).isEqualTo(ReceptionMode.HUMAN);
        assertThat(service.isHumanMode(result)).isTrue();
        verify(conversations).save(result);
    }

    @Test
    void triggeringHandoffTurnIsBlockedWithoutPublishingFreeFormHermesText() {
        HermesSessionService hermes = mock(HermesSessionService.class);
        ReceptionConversationGateway conversations = mock(ReceptionConversationGateway.class);
        CustomerFactGateway facts = mock(CustomerFactGateway.class);
        ReceptionTranscriptGateway transcript = mock(ReceptionTranscriptGateway.class);
        ReceptionTurnGateway turns = mock(ReceptionTurnGateway.class);
        DomainToolInvocationGateway invocations = mock(DomainToolInvocationGateway.class);
        ReceptionConversation ai = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
        ReceptionConversation human = ai.requestHumanHandoff("cliente pediu uma pessoa", NOW.plusSeconds(1));
        when(conversations.findByContactId("poc:ana")).thenReturn(Optional.of(ai), Optional.of(human));
        when(transcript.findByEventId("event-trigger")).thenReturn(Optional.empty());
        when(transcript.appendIfAbsent(any(ReceptionMessage.class))).thenReturn(true);
        when(turns.save(any(ReceptionTurn.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hermes.resolve("poc:ana")).thenReturn(new HermesSessionService.SessionResolution(
                "session-1", false, false, AgentSessionLink.active("poc:ana", "session-1", NOW)));
        when(hermes.chat(eq("poc:ana"), any())).thenReturn(new br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway.HermesChatResult(
                "session-1", "session-1", "{\"message\":\"Vou transferir você agora.\",\"nextAction\":\"HANDOFF\",\"handoffReason\":\"pedido\"}",
                br.com.urbana.connect.domain.reception.model.AgentUsage.empty(), Map.of()));
        when(invocations.findByTurnId(any())).thenReturn(List.of(new DomainToolInvocation(
                "inv-1", "key-1", "turn-1", "session-1", "poc:ana", DomainToolName.REQUEST_HUMAN_HANDOFF,
                "hash", DomainToolInvocationStatus.SUCCEEDED, "OK", NOW, NOW)));

        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(hermes, conversations, facts, transcript,
                turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(), null, invocations,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "event-trigger", "poc:ana", ReceptionMessageType.TEXT, "quero uma pessoa", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.BLOCKED_BY_HUMAN);
        assertThat(receipt.output()).isNull();
        ArgumentCaptor<ReceptionMessage> messages = ArgumentCaptor.forClass(ReceptionMessage.class);
        verify(transcript, times(2)).appendIfAbsent(messages.capture());
        assertThat(messages.getAllValues()).filteredOn(message -> message.senderType() == ReceptionMessageSender.URBA)
                .singleElement().extracting(ReceptionMessage::text)
                .isEqualTo("Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.");
        assertThat(messages.getAllValues()).noneMatch(message -> "Vou transferir você agora.".equals(message.text()));
        ArgumentCaptor<ReceptionTurn> saved = ArgumentCaptor.forClass(ReceptionTurn.class);
        verify(turns, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).status()).isEqualTo(ReceptionTurnStatus.BLOCKED_BY_HUMAN);
    }
}
