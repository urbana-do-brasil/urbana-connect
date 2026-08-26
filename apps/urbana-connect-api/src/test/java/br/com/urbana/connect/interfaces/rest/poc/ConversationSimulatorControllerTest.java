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
import br.com.urbana.connect.domain.reception.model.ResumeStatus;
import br.com.urbana.connect.domain.reception.port.out.HermesResumeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
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
    void exposesBackendControlledHumanMessageAndIdempotentReturnRoutesBehindThePocToken() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        PocReceptionIngress ingress = mock(PocReceptionIngress.class);
        HermesResumeGateway resumeGateway = mock(HermesResumeGateway.class);
        when(orchestrator.recordHumanMessage(eq("poc:ana"), eq("human-1"), eq("Decisão humana"),
                eq(Instant.parse("2026-08-05T12:00:00Z"))))
                .thenReturn(new ReceptionOrchestrator.HumanMessageReceipt(
                        "human-event-1", "RECORDED", false, "Mensagem humana registrada."));
        when(orchestrator.returnToUrba(eq("poc:ana"), eq("return-1"), eq(7L), same(resumeGateway)))
                .thenReturn(new ReceptionOrchestrator.ResumeReceipt(
                        "resume-1", ResumeStatus.COMPLETED, "URBA", null, false,
                        "A Urba retomou o atendimento."));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ConversationSimulatorController(orchestrator, ingress, "poc-token", resumeGateway)).build();

        mvc.perform(post("/api/poc/conversations/ana/human/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer poc-token")
                        .header("Idempotency-Key", "human-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Decisão humana\",\"occurredAt\":\"2026-08-05T12:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.message").value("Mensagem humana registrada."));

        mvc.perform(post("/api/poc/conversations/ana/ownership/urba")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer poc-token")
                        .header("Idempotency-Key", "return-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownership").value("URBA"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(orchestrator).recordHumanMessage("poc:ana", "human-1", "Decisão humana",
                Instant.parse("2026-08-05T12:00:00Z"));
        verify(orchestrator).returnToUrba("poc:ana", "return-1", 7L, resumeGateway);
    }

    @Test
    void protectsOperatorRoutesWithTheSamePocToken() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        PocReceptionIngress ingress = mock(PocReceptionIngress.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ConversationSimulatorController(orchestrator, ingress, "poc-token", mock(HermesResumeGateway.class)))
                .build();

        mvc.perform(post("/api/poc/conversations/ana/human/messages")
                        .header("Idempotency-Key", "human-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Decisão\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/poc/conversations/ana/ownership/urba")
                        .header("Idempotency-Key", "return-1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orchestrator);
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
    void exposesOnlyTheSafeLatestTurnSummary() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("status", "RECONCILING");
        turn.put("correlationId", "corr-safe");
        turn.put("attempt", 1);
        turn.put("retryAllowed", false);
        turn.put("failureClass", "HERMES_TIMEOUT_AFTER_DISPATCH");
        turn.put("acceptedAt", "2026-08-07T12:00:00Z");
        turn.put("startedAt", "2026-08-07T12:00:01Z");
        turn.put("finishedAt", null);
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("contactId", "poc:ana");
        projection.put("conversation", Map.of());
        projection.put("messages", List.of());
        projection.put("turn", turn);
        when(orchestrator.projection("poc:ana")).thenReturn(projection);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConversationSimulatorController(orchestrator)).build();

        mvc.perform(get("/api/poc/conversations/ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turn.status").value("RECONCILING"))
                .andExpect(jsonPath("$.turn.correlationId").value("corr-safe"))
                .andExpect(jsonPath("$.turn.attempt").value(1))
                .andExpect(jsonPath("$.turn.retryAllowed").value(false))
                .andExpect(jsonPath("$.turn.failureClass").value("HERMES_TIMEOUT_AFTER_DISPATCH"))
                .andExpect(jsonPath("$.turn.hermesSessionId").doesNotExist())
                .andExpect(jsonPath("$.turn.output").doesNotExist())
                .andExpect(jsonPath("$.turn.error").doesNotExist());
    }

    @Test
    void returnsAProjectionWithNullTurnForANewContact() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("contactId", "poc:ana");
        projection.put("conversation", Map.of());
        projection.put("messages", List.of());
        projection.put("turn", null);
        when(orchestrator.projection("poc:ana")).thenReturn(projection);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConversationSimulatorController(orchestrator)).build();

        mvc.perform(get("/api/poc/conversations/ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turn").value(nullValue()));
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
