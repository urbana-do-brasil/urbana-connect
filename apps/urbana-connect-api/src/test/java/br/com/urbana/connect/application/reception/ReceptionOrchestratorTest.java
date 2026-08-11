package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus;
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
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceptionOrchestratorTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void persistsInboundBeforeDispatchAndPublishesHermesTextVerbatim() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        String hermesText = "  Resposta do Hermes — exata!  \n";
        String inboundText = "  oi  com  espaços consecutivos  \n  e margem  ";
        CapturingSessions sessions = new CapturingSessions(hermesText);
        sessions.beforeResponse = () -> assertThat(transcript.messages)
                .extracting(ReceptionMessage::direction)
                .containsExactly(ReceptionMessageDirection.INBOUND);
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "event-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                inboundText, NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(receipt.output().message()).isEqualTo(hermesText);
        assertThat(transcript.messages).extracting(ReceptionMessage::direction)
                .containsExactlyInAnyOrder(ReceptionMessageDirection.INBOUND, ReceptionMessageDirection.OUTBOUND);
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text).isEqualTo(hermesText);
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.INBOUND)
                .singleElement().extracting(ReceptionMessage::text).isEqualTo(inboundText);
        assertThat(sessions.lastInput).isEqualTo(inboundText);
        @SuppressWarnings("unchecked")
        List<ReceptionMessage> projectedMessages = (List<ReceptionMessage>) orchestrator
                .projection("poc:ana").get("messages");
        assertThat(projectedMessages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text).isEqualTo(hermesText);
        assertThat(sessions.chatCalls).isEqualTo(1);
    }

    @Test
    void keepsLiteralHermesTextAcrossThreeSequentialTurnsWithoutLocalPrefixOrFallback() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        FakeSessions sessions = new FakeSessions("Resposta textual do Hermes");
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, new MemoryTurns(), new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        orchestrator.process(new InboundConversationEvent(
                "sequence-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "primeiro", NOW));
        orchestrator.process(new InboundConversationEvent(
                "sequence-2", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "segundo", NOW.plusSeconds(1)));
        orchestrator.process(new InboundConversationEvent(
                "sequence-3", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "terceiro", NOW.plusSeconds(2)));

        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .extracting(ReceptionMessage::text)
                .containsExactly("Resposta textual do Hermes", "Resposta textual do Hermes", "Resposta textual do Hermes")
                .allMatch(text -> !text.contains("Olá! Sou a Urba")
                        && !text.contains("Não consigo confirmar"));
        assertThat(sessions.chatCalls).isEqualTo(3);
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
    void sendsIdentityQuestionsToHermesInsteadOfApplyingNonProspectPolicyLocally() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("Hermes handled the identity question");
        MemoryFacts facts = new MemoryFacts();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, facts,
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "non-prospect-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "Quem está respondendo por aqui?", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(receipt.output().message()).isEqualTo("Hermes handled the identity question");
        assertThat(sessions.chatCalls).isEqualTo(1);
        assertThat(sessions.createdSessions).isEqualTo(1);
        assertThat(facts.saveCalls).isZero();
        assertThat(conversations.value.selectedService()).isNull();
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(transcript.messages).extracting(ReceptionMessage::direction)
                .containsExactly(ReceptionMessageDirection.INBOUND, ReceptionMessageDirection.OUTBOUND);
    }

    @Test
    void doesNotInterceptGeneralOrInstitutionalMessagesBeforeHermes() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        FakeSessions sessions = new FakeSessions("Hermes owns this conversation");
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

        assertThat(identity.output().message()).isEqualTo("Hermes owns this conversation");
        assertThat(probe.output().message()).isEqualTo("Hermes owns this conversation");
        assertThat(handoff.output().message()).isEqualTo("Hermes owns this conversation");
        assertThat(sessions.chatCalls).isEqualTo(3);
        assertThat(sessions.createdSessions).isEqualTo(1);
        assertThat(facts.saveCalls).isZero();
        assertThat(conversations.value.mode()).isEqualTo(
                br.com.urbana.connect.domain.reception.model.ReceptionMode.AI);
        assertThat(conversations.value.selectedService()).isNull();
    }

    @Test
    void treatsPlainHermesTextAsConversationWithoutAFalseAssistantMessage() {
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

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(receipt.output().message()).isEqualTo("not-json");
        assertThat(conversations.value.mode()).isEqualTo(br.com.urbana.connect.domain.reception.model.ReceptionMode.AI);
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text).isEqualTo("not-json");
    }

    @Test
    void rejectsBlankHermesResponseAsATechnicalFailureWithoutOutboundMessage() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("  \n\t");
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "empty-response-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.FAILED_TERMINAL);
        assertThat(receipt.output()).isNull();
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND).isEmpty();
        assertThat(turns.values.values()).singleElement()
                .extracting(ReceptionTurn::status)
                .isEqualTo(ReceptionTurnStatus.FAILED_TERMINAL);
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

        assertThat(proof.output().message()).isEqualTo("recovered");
        assertThat(proof.output().nextAction()).isEqualTo(br.com.urbana.connect.domain.reception.model.AgentNextAction.NONE);
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(approval.output().message()).contains("DECOR");
        assertThat(sessions.chatCalls).isEqualTo(1);
        assertThat(sessions.lastInput).isEmpty();
    }

    @Test
    void sendsOnlyTheCurrentMessageWhenInteractiveSelectionWasNotStoredAsAFact() {
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

        assertThat(sessions.lastInput).isEqualTo("PIX");
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
        assertThat(duplicate.output().message()).isEqualTo("ok");
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
        assertThat(sessions.lastInput).isEqualTo("oi");
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
        assertThat(receipt.output().message()).isEqualTo("recovered");
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

    @Test
    void projectionAddsOnlyTheSafeLatestTurnSummaryAndHandlesNoTurn() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
        MemoryTurns turns = new MemoryTurns();
        turns.save(ReceptionTurn.queued("turn-1", "corr-safe", "poc:ana", "session-secret",
                        List.of("message-1"), NOW, null)
                .start(NOW.plusSeconds(1))
                .reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", NOW.plusSeconds(2)));
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()), conversations,
                new MemoryFacts(), new MemoryTranscript(), turns, new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), null, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        Map<String, Object> projection = orchestrator.projection("poc:ana");

        Map<?, ?> turn = (Map<?, ?>) projection.get("turn");
        assertThat(turn.get("status")).isEqualTo("RECONCILING");
        assertThat(turn.get("correlationId")).isEqualTo("corr-safe");
        assertThat(turn.get("attempt")).isEqualTo(1);
        assertThat(turn.get("retryAllowed")).isEqualTo(false);
        assertThat(turn.get("failureClass")).isEqualTo("HERMES_TIMEOUT_AFTER_DISPATCH");
        assertThat(turn.containsKey("acceptedAt")).isTrue();
        assertThat(turn.containsKey("startedAt")).isTrue();
        assertThat(turn.containsKey("finishedAt")).isTrue();
        assertThat(turn.containsKey("hermesSessionId")).isFalse();
        assertThat(turn.containsKey("output")).isFalse();
        assertThat(turn.containsKey("error")).isFalse();
        assertThat(turn.containsKey("failureCode")).isFalse();

        Map<String, Object> emptyProjection = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()),
                new MemoryConversation(), new MemoryFacts(), new MemoryTranscript(), new MemoryTurns(),
                new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC)).projection("poc:new");
        assertThat(emptyProjection).containsEntry("turn", null);
    }

    @Test
    void classifiesAmbiguousHermesFailureAsReconcilingWithoutFallbackOrRedispatch() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        MemoryLeaseGateway leaseGateway = new MemoryLeaseGateway();
        FakeSessions sessions = new FakeSessions("unused") {
            @Override
            public HermesChatResult chat(String sessionId, HermesChatRequest request) {
                chatCalls++;
                throw br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesSessionsException
                        .ambiguous("Hermes read timeout after dispatch");
            }

            @Override
            public HermesHistorySnapshot historySnapshot(String sessionId) {
                return new HermesHistorySnapshot("before-1",
                        List.of(new HermesHistoryMessage("user", "oi")));
            }
        };
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                new ActiveTurnLeaseService(leaseGateway, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30)),
                null, Clock.fixed(NOW, ZoneOffset.UTC), new ReceptionMetrics(), null, new NonProspectPolicy());

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "ambiguous-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.RECONCILING);
        assertThat(receipt.output()).isNull();
        assertThat(sessions.chatCalls).isEqualTo(1);
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND).isEmpty();
        assertThat(turns.values.values()).singleElement().satisfies(turn -> {
            assertThat(turn.status()).isEqualTo(ReceptionTurnStatus.RECONCILING);
            assertThat(turn.retryAllowed()).isFalse();
            assertThat(turn.historyCheckpoint()).isEqualTo("before-1|1");
        });
        assertThat(leaseGateway.current("session-1").status()).isEqualTo(ActiveTurnLeaseStatus.RECONCILING);
    }

    @Test
    void persistsDelayedWhileBlockingChatRunsAndLeavesTheTurnCompletedAfterwards() throws Exception {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        BlockingSessions sessions = new BlockingSessions();
        ReceptionMetrics metrics = new ReceptionMetrics();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                null, null, java.time.Clock.systemUTC(), metrics, null, new NonProspectPolicy(),
                Duration.ofMillis(60));
        InboundConversationEvent event = new InboundConversationEvent(
                "slow-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", Instant.now());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<ReceptionOrchestrator.TurnReceipt> result = executor.submit(() -> orchestrator.process(event));
            assertThat(sessions.chatStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitStatus(turns, ReceptionTurnStatus.DELAYED, 1_000)).isTrue();
            assertThat(awaitMetric(metrics, 1_000)).isTrue();

            sessions.releaseChat.countDown();
            assertThat(result.get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
            assertThat(turns.values).hasSize(1);
            assertThat(turns.values.values()).singleElement()
                    .extracting(ReceptionTurn::status).isEqualTo(ReceptionTurnStatus.COMPLETED);
        } finally {
            sessions.releaseChat.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void doesNotOverwriteAConcurrentReconcilingTurnWhenTheBlockingChatReturns() throws Exception {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        BlockingSessions sessions = new BlockingSessions();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                null, null, java.time.Clock.systemUTC(), new ReceptionMetrics(), null,
                new NonProspectPolicy(), Duration.ofMillis(60));
        InboundConversationEvent event = new InboundConversationEvent(
                "reconciling-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", Instant.now());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<ReceptionOrchestrator.TurnReceipt> result = executor.submit(() -> orchestrator.process(event));
            assertThat(sessions.chatStarted.await(1, TimeUnit.SECONDS)).isTrue();
            ReceptionTurn running = turns.values.values().stream().findFirst().orElseThrow();
            turns.save(running.reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", Instant.now()));
            sessions.releaseChat.countDown();

            assertThat(result.get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(ReceptionOrchestrator.TurnStatus.RECONCILING);
            assertThat(turns.values.values()).singleElement()
                    .extracting(ReceptionTurn::status).isEqualTo(ReceptionTurnStatus.RECONCILING);
            assertThat(transcript.messages).filteredOn(message ->
                    message.direction() == ReceptionMessageDirection.OUTBOUND).isEmpty();
        } finally {
            sessions.releaseChat.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void returnsSanitizedTerminalTechnicalFailureWithoutConversationalOutput() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("unused") {
            @Override
            public HermesChatResult chat(String sessionId, HermesChatRequest request) {
                throw new IllegalStateException("secret=provider-token customer prompt");
            }
        };
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "terminal-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.FAILED_TERMINAL);
        assertThat(receipt.output()).isNull();
        assertThat(receipt.error()).isEqualTo("APPLICATION_FAILURE");
        assertThat(receipt.error()).doesNotContain("secret", "provider-token", "customer prompt");
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND).isEmpty();
        assertThat(turns.values.values()).singleElement().satisfies(turn -> {
            assertThat(turn.status()).isEqualTo(ReceptionTurnStatus.FAILED_TERMINAL);
            assertThat(turn.retryAllowed()).isFalse();
            assertThat(turn.failureClass()).isEqualTo("APPLICATION_FAILURE");
        });
    }

    @Test
    void keepsPreDispatchFailureRetryableWithoutReturningTheRawHermesMessage() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("unused") {
            @Override
            public HermesChatResult chat(String sessionId, HermesChatRequest request) {
                throw br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesSessionsException
                        .preDispatch("secret=provider-token");
            }
        };
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(),
                transcript, turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "retry-safe-1", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "oi", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.FAILED_SAFE_TO_RETRY);
        assertThat(receipt.output()).isNull();
        assertThat(receipt.error()).isEqualTo("HERMES_REJECTED_BEFORE_DISPATCH");
        assertThat(receipt.error()).doesNotContain("secret", "provider-token");
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND).isEmpty();
        assertThat(turns.values.values()).singleElement()
                .extracting(ReceptionTurn::retryAllowed).isEqualTo(true);
    }

    private static boolean awaitStatus(MemoryTurns turns, ReceptionTurnStatus expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (turns.values.values().stream().anyMatch(turn -> turn.status() == expected)) return true;
            Thread.sleep(10);
        }
        return turns.values.values().stream().anyMatch(turn -> turn.status() == expected);
    }

    private static boolean awaitMetric(ReceptionMetrics metrics, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (metrics.snapshot().delayedTurns() == 1) return true;
            Thread.sleep(10);
        }
        return metrics.snapshot().delayedTurns() == 1;
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
            this("{\"message\":\"recovered\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        }
        CapturingSessions(String response) {
            super(response);
        }
        @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) {
            lastInput = request.input();
            return super.chat(sessionId, request);
        }
    }

    private static final class BlockingSessions extends FakeSessions {
        final CountDownLatch chatStarted = new CountDownLatch(1);
        final CountDownLatch releaseChat = new CountDownLatch(1);

        BlockingSessions() {
            super("{\"message\":\"slow response\",\"nextAction\":\"AWAIT_CUSTOMER\"}");
        }

        @Override
        public HermesChatResult chat(String sessionId, HermesChatRequest request) {
            chatCalls++;
            chatStarted.countDown();
            try {
                releaseChat.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("blocking chat interrupted", interrupted);
            }
            return new HermesChatResult(sessionId, sessionId, response, AgentUsage.empty(), Map.of());
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
        final Map<String, ReceptionTurn> values = new ConcurrentHashMap<>();
        @Override public ReceptionTurn save(ReceptionTurn turn) { values.put(turn.id(), turn); return turn; }
        @Override public Optional<ReceptionTurn> findById(String turnId) { return Optional.ofNullable(values.get(turnId)); }
        @Override public Optional<ReceptionTurn> findByInboundMessageId(String messageId) { return values.values().stream().filter(t -> t.inboundMessageIds().contains(messageId)).findFirst(); }
        @Override public Optional<ReceptionTurn> findLatestByContactId(String contactId) {
            return values.values().stream().filter(turn -> turn.contactId().equals(contactId))
                    .max(java.util.Comparator.comparing(ReceptionTurn::acceptedAt));
        }
    }

    private static final class MemoryLeaseGateway implements ActiveTurnLeaseGateway {
        private final Map<String, ActiveTurnLease> values = new HashMap<>();

        @Override
        public synchronized Optional<ActiveTurnLease> acquire(ActiveTurnLease requested) {
            ActiveTurnLease current = values.get(requested.hermesSessionId());
            if (current != null && current.blocksNewTurnAt(requested.acquiredAt())) {
                return Optional.empty();
            }
            values.put(requested.hermesSessionId(), requested);
            return Optional.of(requested);
        }

        @Override
        public synchronized Optional<ActiveTurnLease> findRunning(String sessionId, Instant at) {
            ActiveTurnLease value = values.get(sessionId);
            return value != null && value.isActiveAt(at) ? Optional.of(value) : Optional.empty();
        }

        @Override
        public synchronized Optional<ActiveTurnLease> findBlocking(String sessionId, Instant at) {
            ActiveTurnLease value = values.get(sessionId);
            return value != null && value.blocksNewTurnAt(at) ? Optional.of(value) : Optional.empty();
        }

        @Override
        public synchronized ActiveTurnLease markReconciling(String sessionId, String turnId,
                                                             String claimToken, Instant at) {
            ActiveTurnLease value = values.get(sessionId);
            if (value == null || !value.turnId().equals(turnId) || !value.claimToken().equals(claimToken)) {
                throw new IllegalArgumentException("lease claim binding mismatch");
            }
            ActiveTurnLease next = value.reconcile(at);
            values.put(sessionId, next);
            return next;
        }

        @Override
        public synchronized ActiveTurnLease revoke(String sessionId, String turnId, String claimToken,
                                                   Instant at) {
            ActiveTurnLease value = values.get(sessionId);
            if (value == null || !value.turnId().equals(turnId)
                    || (claimToken != null && !claimToken.equals(value.claimToken()))) {
                throw new IllegalArgumentException("lease binding mismatch");
            }
            ActiveTurnLease next = value.revoke(at);
            values.put(sessionId, next);
            return next;
        }

        @Override
        public synchronized ActiveTurnLease revoke(String sessionId, String turnId, Instant at) {
            return revoke(sessionId, turnId, null, at);
        }

        @Override
        public synchronized ActiveTurnLease expire(String sessionId, String turnId, Instant at) {
            ActiveTurnLease value = values.get(sessionId);
            ActiveTurnLease next = value.expire(at);
            values.put(sessionId, next);
            return next;
        }

        ActiveTurnLease current(String sessionId) {
            return values.get(sessionId);
        }
    }
}
