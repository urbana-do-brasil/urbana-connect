package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocationStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.domain.reception.model.CommercialStage;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceptionOrchestratorTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void persistsInboundAndPublishesTheParsedHermesContractWithoutAuthoringDialogue() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("{\"message\":\"Resposta do Hermes\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "event-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(receipt.output().message()).isEqualTo(
                "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Resposta do Hermes");
        assertThat(transcript.messages).extracting(ReceptionMessage::direction)
                .containsExactlyInAnyOrder(ReceptionMessageDirection.INBOUND, ReceptionMessageDirection.OUTBOUND);
        assertThat(sessions.chatCalls).isEqualTo(1);
    }

    @Test
    void appliesAcceptanceAfterHermesPresentsTermsDuringTheSameTurn() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        CommercialPolicyService policy = new CommercialPolicyService();
        FakeSessions sessions = new FakeSessions("{\"message\":\"Termos registrados\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        sessions.beforeResponse = () -> conversations.save(new ReceptionConversation(
                "conversation-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMode.AI,
                CommercialStage.TERMS, "DECOR", TermsStatus.PRESENTED, PaymentStatus.NOT_STARTED,
                null, NOW, NOW.plusSeconds(1), 1));
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, policy, new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "terms-acceptance", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "Aceito os termos", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.ACCEPTED);
    }

    @Test
    void presentsTermsAfterACompleteIcpAndApprovedServiceArePersisted() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = new ReceptionConversation("conversation-1", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMode.AI, CommercialStage.ICP,
                "DECOR", TermsStatus.NOT_PRESENTED, PaymentStatus.NOT_STARTED, null, NOW, NOW, 1);
        List<CustomerFact> icp = List.of(
                CustomerFact.confirmed("poc:ana", "PRONOUN_PREFERENCE", "ELA_DELA", "m-1", NOW),
                CustomerFact.confirmed("poc:ana", "FIRST_TIME_HIRING", "YES", "m-2", NOW),
                CustomerFact.confirmed("poc:ana", "OCCUPATION", "DESIGNER", "m-3", NOW));
        CustomerFactGateway facts = mock(CustomerFactGateway.class);
        when(facts.findCurrentByContactId("poc:ana", NOW)).thenReturn(icp);
        when(facts.findByContactId("poc:ana")).thenReturn(icp);
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("{\"message\":\"ok\",\"nextAction\":\"AWAIT_CUSTOMER\"}"),
                        new MemoryLinks()), conversations, facts, new MemoryTranscript(), new MemoryTurns(),
                new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "service-selected", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "Decor", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
    }

    @Test
    void appliesNonProspectPolicyBeforeHermesWithoutCreatingIcp() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("{\"message\":\"should not be used\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        MemoryFacts facts = new MemoryFacts();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, facts,
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "non-prospect-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "Quem está respondendo por aqui?", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(receipt.output().message()).contains("Urba", "Urbana do Brasil");
        assertThat(sessions.chatCalls).isZero();
        assertThat(sessions.createdSessions).isZero();
        assertThat(facts.findCalls).isZero();
        assertThat(facts.saveCalls).isZero();
        assertThat(conversations.value.selectedService()).isNull();
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(transcript.messages).extracting(ReceptionMessage::direction)
                .containsExactly(ReceptionMessageDirection.INBOUND, ReceptionMessageDirection.OUTBOUND);
    }

    @Test
    void keepsNonProspectProbeAndInstitutionalHandoffLocalToThePolicyFlow() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        FakeSessions sessions = new FakeSessions("{\"message\":\"should not be used\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, facts,
                new MemoryTranscript(), new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt identity = orchestrator.process(new InboundConversationEvent(
                "non-prospect-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "Quem está respondendo por aqui?", NOW));
        ReceptionOrchestrator.TurnReceipt probe = orchestrator.process(new InboundConversationEvent(
                "non-prospect-2", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "Tenho uma dúvida geral; posso explicar o assunto?", NOW.plusSeconds(5)));
        ReceptionOrchestrator.TurnReceipt handoff = orchestrator.process(new InboundConversationEvent(
                "non-prospect-3", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "É uma parceria institucional.", NOW.plusSeconds(10)));

        assertThat(identity.output().nextAction()).isEqualTo(
                br.com.urbana.connect.domain.reception.model.AgentNextAction.AWAIT_CUSTOMER);
        assertThat(probe.output().message()).contains("assunto");
        assertThat(handoff.output().nextAction()).isEqualTo(
                br.com.urbana.connect.domain.reception.model.AgentNextAction.HANDOFF);
        assertThat(sessions.chatCalls).isZero();
        assertThat(sessions.createdSessions).isZero();
        assertThat(facts.findCalls).isZero();
        assertThat(facts.saveCalls).isZero();
        assertThat(conversations.value.mode()).isEqualTo(
                br.com.urbana.connect.domain.reception.model.ReceptionMode.HUMAN);
        assertThat(conversations.value.selectedService()).isNull();
    }

    @Test
    void invalidHermesJsonGetsRetryMessageInsteadOfAFalseHumanHandoff() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        FakeSessions sessions = new FakeSessions("not-json");
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, new MemoryTurns(), new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "event-2", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", NOW));

        assertThat(receipt.output().nextAction()).isEqualTo(br.com.urbana.connect.domain.reception.model.AgentNextAction.AWAIT_CUSTOMER);
        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(conversations.value.mode()).isEqualTo(br.com.urbana.connect.domain.reception.model.ReceptionMode.AI);
    }

    @Test
    void proofWaitsForHumanApprovalAndOnlyThenReleasesTheSelectedServiceBriefing() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = new ReceptionConversation("conversation-1", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMode.AI, CommercialStage.PAYMENT,
                "DECOR", TermsStatus.ACCEPTED, PaymentStatus.PREPARED, null, NOW, NOW, 0);
        MemoryTranscript transcript = new MemoryTranscript();
        CapturingSessions sessions = new CapturingSessions();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, new MemoryTurns(), new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt proof = orchestrator.process(new InboundConversationEvent(
                "proof-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.PAYMENT_PROOF,
                null, null, "poc/payment-proof-fixture.png", null, NOW, null));
        ReceptionOrchestrator.TurnReceipt approval = orchestrator.approvePaymentProof("poc:ana");

        assertThat(proof.output().nextAction()).isEqualTo(br.com.urbana.connect.domain.reception.model.AgentNextAction.AWAIT_PAYMENT_APPROVAL);
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(approval.output().message()).contains("DECOR");
        assertThat(sessions.chatCalls).isEqualTo(1);
        assertThat(sessions.lastInput).containsIgnoringCase("comprovante").doesNotContain("payment-proof");
    }

    @Test
    void sendsCanonicalCommercialStateWhenInteractiveSelectionWasNotStoredAsAFact() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = new ReceptionConversation("conversation-1", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMode.AI, CommercialStage.PAYMENT,
                "DECOR", TermsStatus.ACCEPTED, PaymentStatus.NOT_STARTED, null, NOW, NOW, 1);
        CapturingSessions sessions = new CapturingSessions();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                new MemoryTranscript(), new MemoryTurns(), new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        orchestrator.process(new InboundConversationEvent(
                "payment-method", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "PIX", NOW));

        assertThat(sessions.lastInput)
                .contains("SELECTED_SERVICE=DECOR", "TERMS_STATUS=ACCEPTED", "PAYMENT_STATUS=NOT_STARTED")
                .contains("MISSING_ICP_FIELDS=PRONOUN_PREFERENCE,FIRST_TIME_HIRING,OCCUPATION")
                .contains("Mensagem atual: PIX");
    }

    @Test
    void persistsInteractiveServiceSelectionAsAConfirmedVersionedFact() {
        MemoryConversation conversations = new MemoryConversation();
        CustomerFactGateway facts = mock(CustomerFactGateway.class);
        when(facts.findCurrentByContactId("poc:ana", NOW)).thenReturn(List.of());
        when(facts.findByContactId("poc:ana")).thenReturn(List.of());
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("{\"message\":\"ok\",\"nextAction\":\"AWAIT_CUSTOMER\"}"),
                        new MemoryLinks()), conversations, facts, new MemoryTranscript(), new MemoryTurns(),
                new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        orchestrator.process(new InboundConversationEvent(
                "service-choice", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.INTERACTIVE,
                "Decor", null, null, "service.decor", NOW, null));

        verify(facts).save(argThat(fact -> fact.type().equals("SELECTED_SERVICE")
                && fact.value().equals("DECOR")
                && fact.confidence() == br.com.urbana.connect.domain.reception.model.FactConfidence.CONFIRMED
                && fact.sourceMessageId().equals("service-choice")));
    }

    @Test
    void duplicateCanonicalEventReturnsAnExplicitDuplicateWithoutFabricatingAnAction() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("{\"message\":\"ok\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()),
                conversations, new MemoryFacts(), transcript, turns, new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        InboundConversationEvent event = new InboundConversationEvent("duplicate-1", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT, "oi", NOW);

        orchestrator.process(event);
        ReceptionOrchestrator restarted = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        // Reuse the persisted transcript/turn stores; the process-local
        // coordinator is intentionally empty after the simulated restart.
        ReceptionOrchestrator.TurnReceipt duplicate = restarted.process(event);

        assertThat(duplicate.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.DUPLICATE);
        assertThat(duplicate.output().message()).isEqualTo(
                "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. ok");
    }

    @Test
    void deduplicatesRepeatedEventIdsInsideOneReleasedBatchBeforeChat() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        CapturingSessions sessions = new CapturingSessions();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        InboundConversationEvent event = new InboundConversationEvent("batch-duplicate", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT, "oi", NOW);

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.processBatch(List.of(event, event));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(sessions.lastInput).contains("Mensagem atual: oi");
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.INBOUND).hasSize(1);
        assertThat(turns.values.values()).singleElement().satisfies(turn ->
                assertThat(turn.inboundMessageIds()).hasSize(1));
    }

    @Test
    void recoversPersistedInboundWhenTheProcessCrashedBeforeSavingItsTurn() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("message-crashed", "crashed-inbound", "corr-crashed",
                "conversation-1", "poc:ana", ReceptionMessageDirection.INBOUND,
                br.com.urbana.connect.domain.reception.model.ReceptionMessageSender.CONTACT,
                br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", null, null, NOW));
        MemoryTurns turns = new MemoryTurns();
        CapturingSessions sessions = new CapturingSessions();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "crashed-inbound", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(receipt.output().message()).isEqualTo(
                "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. recovered");
        assertThat(sessions.chatCalls).isEqualTo(1);
        assertThat(transcript.messages).extracting(ReceptionMessage::direction)
                .containsExactly(ReceptionMessageDirection.INBOUND, ReceptionMessageDirection.OUTBOUND);
        assertThat(turns.values.values()).singleElement()
                .extracting(ReceptionTurn::status)
                .isEqualTo(ReceptionTurnStatus.COMPLETED);
    }

    @Test
    void allowsOperatorPaymentApprovalAfterHumanHandoff() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = new ReceptionConversation("conversation-human-payment", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMode.HUMAN,
                CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED, PaymentStatus.PROOF_RECEIVED,
                "cliente pediu atendimento humano", NOW, NOW, 0);
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()), conversations,
                new MemoryFacts(), new MemoryTranscript(), new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.approvePaymentProof("poc:ana");

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(receipt.output().message()).contains("DECOR");
        assertThat(conversations.value.mode()).isEqualTo(
                br.com.urbana.connect.domain.reception.model.ReceptionMode.HUMAN);
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void rejectsAnEventIdCollisionAcrossContactsWithoutLeakingThePriorOutput() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("{\"message\":\"private response\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        orchestrator.process(new InboundConversationEvent("shared-event", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT, "segredo", NOW));

        assertThatThrownBy(() -> orchestrator.process(new InboundConversationEvent("shared-event", "poc:bia",
                br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT, "oi", NOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another contact");
        assertThat(sessions.chatCalls).isEqualTo(1);
    }

    @Test
    void outboundReceiptNamespaceCannotConsumeAValidFutureInboundEventId() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("{\"message\":\"ok\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        orchestrator.process(new InboundConversationEvent("e", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT, "primeiro", NOW));
        ReceptionOrchestrator.TurnReceipt second = orchestrator.process(new InboundConversationEvent("e:outbound", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT, "segundo", NOW.plusSeconds(1)));

        assertThat(second.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(sessions.chatCalls).isEqualTo(2);
        assertThat(transcript.messages.stream()
                .filter(message -> message.direction() == ReceptionMessageDirection.INBOUND))
                .hasSize(2);
    }

    @Test
    void projectionExposesOnlySafeToolEvidenceAndPocCounters() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
        DomainToolInvocationGateway invocations = mock(DomainToolInvocationGateway.class);
        when(invocations.findByContactId("poc:ana")).thenReturn(List.of(new DomainToolInvocation(
                "invocation-1", "idempotency-1", "turn-1", "session-1", "poc:ana",
                DomainToolName.REQUEST_HUMAN_HANDOFF, "arguments-hash", DomainToolInvocationStatus.SUCCEEDED,
                "HANDOFF_REQUESTED", Map.of("reason", "sensitive"), NOW, NOW)));
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()), conversations,
                new MemoryFacts(), new MemoryTranscript(), new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), null, invocations,
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC), new ReceptionMetrics(), null,
                new NonProspectPolicy());

        Map<String, Object> projection = orchestrator.projection("poc:ana");

        assertThat(projection).containsKeys("toolInvocations", "hermesChatCalls", "lightProbesUsed",
                "commercialOpportunityCreated");
        assertThat(projection.get("hermesChatCalls")).isEqualTo(0L);
        assertThat(projection.get("lightProbesUsed")).isEqualTo(0);
        assertThat(projection.get("commercialOpportunityCreated")).isEqualTo(false);
        Map<?, ?> tool = ((List<Map<?, ?>>) projection.get("toolInvocations")).getFirst();
        assertThat(tool.get("toolName")).isEqualTo("request_human_handoff");
        assertThat(tool.get("status")).isEqualTo("SUCCEEDED");
        assertThat(tool.get("resultCode")).isEqualTo("HANDOFF_REQUESTED");
        assertThat(tool.containsKey("contactId")).isFalse();
        assertThat(tool.containsKey("argumentsHash")).isFalse();
        assertThat(tool.containsKey("resultPayload")).isFalse();
    }

    private static class FakeSessions implements HermesSessionsGateway {
        final String response; int chatCalls; int createdSessions;
        Runnable beforeResponse = () -> { };
        FakeSessions(String response) { this.response = response; }
        @Override public String createSession(String contactId) { createdSessions++; return "session-1"; }
        @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) {
            chatCalls++;
            beforeResponse.run();
            return new HermesChatResult(sessionId, sessionId, response, AgentUsage.empty(), Map.of());
        }
        @Override public List<HermesHistoryMessage> history(String sessionId) { return List.of(); }
    }

    private static final class CapturingSessions extends FakeSessions {
        String lastInput;
        CapturingSessions() {
            super("{\"message\":\"recovered\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        }
        @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) {
            lastInput = request.input();
            return super.chat(sessionId, request);
        }
    }

    private static final class MemoryLinks implements AgentSessionLinkGateway {
        AgentSessionLink value;
        @Override public Optional<AgentSessionLink> findActiveByContactId(String contactId) { return Optional.ofNullable(value); }
        @Override public Optional<AgentSessionLink> findBySessionId(String sessionId) { return Optional.ofNullable(value); }
        @Override public AgentSessionLink createIfAbsent(AgentSessionLink link) { return value == null ? (value = link) : value; }
        @Override public AgentSessionLink touchActive(String contactId, String expectedSessionId, Instant lastUsedAt) { return value = value.touch(lastUsedAt); }
        @Override public AgentSessionLink replaceActive(String contactId, String expectedSessionId, AgentSessionLink replacement,
                                                         br.com.urbana.connect.domain.reception.model.SessionLinkStatus previousStatus) { return value = replacement; }
    }

    private static final class MemoryConversation implements ReceptionConversationGateway {
        ReceptionConversation value;
        @Override public Optional<ReceptionConversation> findByContactId(String contactId) { return Optional.ofNullable(value); }
        @Override public ReceptionConversation save(ReceptionConversation conversation) { return value = conversation; }
    }
    private static final class MemoryFacts implements CustomerFactGateway {
        int findCalls;
        int saveCalls;
        @Override public List<CustomerFact> findCurrentByContactId(String contactId, Instant at) { findCalls++; return List.of(); }
        @Override public List<CustomerFact> findByContactId(String contactId) { findCalls++; return List.of(); }
        @Override public CustomerFact save(CustomerFact fact) { saveCalls++; return fact; }
    }
    private static final class MemoryTranscript implements ReceptionTranscriptGateway {
        final List<ReceptionMessage> messages = new ArrayList<>();
        @Override public boolean appendIfAbsent(ReceptionMessage message) {
            if (findByEventId(message.eventId()).isPresent()) return false;
            messages.add(message); return true;
        }
        @Override public Optional<ReceptionMessage> findByEventId(String eventId) { return messages.stream().filter(m -> m.eventId().equals(eventId)).findFirst(); }
        @Override public List<ReceptionMessage> findByConversationId(String conversationId) { return List.copyOf(messages); }
    }
    private static final class MemoryTurns implements ReceptionTurnGateway {
        final Map<String, ReceptionTurn> values = new HashMap<>();
        @Override public ReceptionTurn save(ReceptionTurn turn) { values.put(turn.id(), turn); return turn; }
        @Override public Optional<ReceptionTurn> findById(String turnId) { return Optional.ofNullable(values.get(turnId)); }
        @Override public Optional<ReceptionTurn> findByInboundMessageId(String messageId) { return values.values().stream().filter(t -> t.inboundMessageIds().contains(messageId)).findFirst(); }
    }
}
