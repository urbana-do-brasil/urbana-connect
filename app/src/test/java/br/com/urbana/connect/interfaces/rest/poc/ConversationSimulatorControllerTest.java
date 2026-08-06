package br.com.urbana.connect.interfaces.rest.poc;

import br.com.urbana.connect.application.reception.InboundConversationEvent;
import br.com.urbana.connect.application.reception.MediaNormalizationService;
import br.com.urbana.connect.application.reception.MessageBatcher;
import br.com.urbana.connect.application.reception.PocReceptionIngress;
import br.com.urbana.connect.application.reception.ReceptionMetrics;
import br.com.urbana.connect.application.reception.ReceptionOrchestrator;
import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.HttpHeaders;

class ConversationSimulatorControllerTest {
    @Test
    void mapsImmediateSyntheticEventThroughPocIngressAndReturnsTurnReceipt() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            List<InboundConversationEvent> events = invocation.getArgument(0);
            return new ReceptionOrchestrator.TurnReceipt(
                    events.getFirst().eventId(), "corr-1", ReceptionOrchestrator.TurnStatus.COMPLETED,
                    new AgentOutput("Olá! Sou a Urba.", AgentNextAction.AWAIT_CUSTOMER), null);
        });
        PocReceptionIngress ingress = new PocReceptionIngress(orchestrator, new MessageBatcher(),
                new MediaNormalizationService(media -> java.util.Optional.empty()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConversationSimulatorController(orchestrator, ingress)).build();

        mvc.perform(post("/api/poc/conversations/ana/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"evt-1","type":"INTERACTIVE","text":"Oi","occurredAt":"2026-08-05T12:00:00Z"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("evt-1"))
                .andExpect(jsonPath("$.correlationId").value("corr-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.output.message").value("Olá! Sou a Urba."));

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(orchestrator).processBatch(captor.capture());
        InboundConversationEvent event = (InboundConversationEvent) captor.getValue().getFirst();
        assertThat(event.contactId()).isEqualTo("poc:ana");
        assertThat(event.type()).isEqualTo(ReceptionMessageType.INTERACTIVE);
    }

    @Test
    void exposesHumanPaymentApprovalAsBackendOnlyAction() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        when(orchestrator.approvePaymentProof(eq("poc:ana"))).thenReturn(new ReceptionOrchestrator.TurnReceipt(
                "approval-poc:ana", "corr-approval", ReceptionOrchestrator.TurnStatus.COMPLETED,
                new AgentOutput("Briefing DECOR liberado.", AgentNextAction.NONE), null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConversationSimulatorController(orchestrator)).build();

        mvc.perform(post("/api/poc/conversations/ana/payment-proof/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output.message").value("Briefing DECOR liberado."));

        verify(orchestrator).approvePaymentProof("poc:ana");
    }

    @Test
    void rejectsMediaPathsOutsideThePocFixtureAllowlist() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConversationSimulatorController(orchestrator)).build();

        mvc.perform(post("/api/poc/conversations/ana/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"evt-1","type":"IMAGE","mediaFixture":"poc/../../etc/passwd","occurredAt":"2026-08-05T12:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOversizedSyntheticContentBeforeCallingTheOrchestrator() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConversationSimulatorController(orchestrator)).build();

        mvc.perform(post("/api/poc/conversations/ana/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt-1\",\"type\":\"TEXT\",\"text\":\""
                                + "x".repeat(8001) + "\",\"occurredAt\":\"2026-08-05T12:00:00Z\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orchestrator);
    }

    @Test
    void forceFlushesPendingPocBatchForDeterministicCorpusProgress() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        PocReceptionIngress ingress = mock(PocReceptionIngress.class);
        when(ingress.forceFlush("poc:ana")).thenReturn(List.of(new ReceptionOrchestrator.TurnReceipt(
                "evt-queued", "corr-flush", ReceptionOrchestrator.TurnStatus.COMPLETED,
                new AgentOutput("ok", AgentNextAction.AWAIT_CUSTOMER), null)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConversationSimulatorController(orchestrator, ingress)).build();

        mvc.perform(post("/api/poc/conversations/ana/flush"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-queued"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        verify(ingress).forceFlush("poc:ana");
    }

    @Test
    void exposesOnlyThePocMetricsSnapshotWithoutConversationContent() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        ReceptionMetrics metrics = new ReceptionMetrics();
        metrics.recordToolInvocation("tool-1");
        when(orchestrator.metrics()).thenReturn(metrics);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConversationSimulatorController(orchestrator)).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/poc/conversations/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolInvocations").value(1))
                .andExpect(jsonPath("$.turns").value(0));
    }

    @Test
    void requiresTheConfiguredBearerTokenForTheSyntheticIngress() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        PocReceptionIngress ingress = mock(PocReceptionIngress.class);
        when(ingress.forceFlush("poc:ana")).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ConversationSimulatorController(orchestrator, ingress, "poc-token")).build();

        mvc.perform(get("/api/poc/conversations/metrics"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/poc/conversations/ana/flush"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/poc/conversations/ana/flush")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer poc-token"))
                .andExpect(status().isOk());

        verify(ingress).forceFlush("poc:ana");
    }
}
