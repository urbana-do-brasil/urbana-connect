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
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.ResumeStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionEventIds;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.domain.reception.model.TermsConsentAudit;
import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import br.com.urbana.connect.domain.reception.model.CommercialStage;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesResumeGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditGateway;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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
    void resumeChecksumUsesUtf8ForSupplementaryUnicodeInsteadOfEscapedSurrogates() throws Exception {
        Method checksum = ReceptionOrchestrator.class.getDeclaredMethod("resumeChecksum", List.class);
        checksum.setAccessible(true);
        @SuppressWarnings("unchecked")
        String actual = (String) checksum.invoke(null, List.of(
                new HermesResumeGateway.ContextMessage(1, "m-1", "CONTACT", "user", "🦕")));

        assertThat(actual).isEqualTo("sha256:109629377e6112df724faa31ac7d0a57771f88fc18ef448ba8da4da6b610436d");
    }

    @Test
    void persistsInboundBeforeDispatchAndAddsIdentityOnlyToTheFirstHermesResponse() {
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

        String expectedFirstResponse = "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. " + hermesText;
        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(receipt.output().message()).isEqualTo(expectedFirstResponse);
        assertThat(transcript.messages).extracting(ReceptionMessage::direction)
                .containsExactlyInAnyOrder(ReceptionMessageDirection.INBOUND, ReceptionMessageDirection.OUTBOUND);
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text).isEqualTo(expectedFirstResponse);
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.INBOUND)
                .singleElement().extracting(ReceptionMessage::text).isEqualTo(inboundText);
        assertThat(sessions.lastInput).isEqualTo(inboundText);
        @SuppressWarnings("unchecked")
        List<ReceptionMessage> projectedMessages = (List<ReceptionMessage>) orchestrator
                .projection("poc:ana").get("messages");
        assertThat(projectedMessages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text).isEqualTo(expectedFirstResponse);
        assertThat(sessions.chatCalls).isEqualTo(1);
    }

    @Test
    void identifiesUrbaAndUrbanaDoBrasilInTheFirstOutboundWhenHermesOmitsTheIntroduction() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("Olá, Emanuel! Como posso ajudar?"), new MemoryLinks()),
                conversations, new MemoryFacts(), transcript, new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "first-message", "poc:emanuel", ReceptionMessageType.TEXT, "Olá", NOW));

        assertThat(receipt.output().message())
                .contains("Urba")
                .contains("Urbana do Brasil")
                .endsWith("Olá, Emanuel! Como posso ajudar?");
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text)
                .isEqualTo(receipt.output().message());
    }

    @Test
    void doesNotDuplicateTheFirstIntroductionWhenHermesAlreadyIdentifiesUrbaAndUrbanaDoBrasil() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        String hermesText = "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Como posso ajudar?";
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions(hermesText), new MemoryLinks()), conversations,
                new MemoryFacts(), transcript, new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "first-message-already-identified", "poc:emanuel", ReceptionMessageType.TEXT, "Olá", NOW));

        assertThat(receipt.output().message()).isEqualTo(hermesText);
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
                .containsExactly(
                        "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Resposta textual do Hermes",
                        "Resposta textual do Hermes",
                        "Resposta textual do Hermes");
        assertThat(sessions.chatCalls).isEqualTo(3);
    }

    @Test
    void doesNotApplyAcceptanceWhenHermesPresentsTermsDuringTheSameTurn() {
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
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
    }

    @Test
    void doesNotTreatBareAcceptanceAsAContractualTermsAcceptance() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = new ReceptionConversation("conversation-1", "poc:ana",
                br.com.urbana.connect.domain.reception.model.ReceptionMode.AI,
                CommercialStage.TERMS, "DECOR", TermsStatus.PRESENTED,
                PaymentStatus.NOT_STARTED, null, NOW, NOW, 1);
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("Ainda preciso do aceite claro"), new MemoryLinks()),
                conversations, new MemoryFacts(), new MemoryTranscript(), new MemoryTurns(),
                new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        orchestrator.process(new InboundConversationEvent(
                "terms-ambiguous", "poc:ana", br.com.urbana.connect.domain.reception.model.ReceptionMessageType.TEXT,
                "Aceito", NOW));

        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
    }

    @Test
    void neverPublishesPaymentApprovalAfterARejectedClaimWhenNoProofWasReceived() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = new ReceptionConversation("conversation-1", "poc:ana",
                ReceptionMode.AI, CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED,
                PaymentStatus.NOT_STARTED, null, NOW, NOW, 1);
        MemoryTranscript transcript = new MemoryTranscript();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("Pagamento confirmado."), new MemoryLinks()),
                conversations, new MemoryFacts(), transcript, new MemoryTurns(),
                new CommercialPolicyService(), new ReceptionTurnCoordinator(),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "premature-payment", "poc:ana", ReceptionMessageType.TEXT,
                "Quero continuar", NOW));

        assertThat(receipt.output().nextAction())
                .isNotEqualTo(br.com.urbana.connect.domain.reception.model.AgentNextAction.AWAIT_PAYMENT_APPROVAL)
                .isEqualTo(br.com.urbana.connect.domain.reception.model.AgentNextAction.AWAIT_CUSTOMER);
        assertThat(receipt.output().message()).doesNotContainIgnoringCase(
                "aguardar a confirmação do pagamento", "sistema", "ferramenta", "loop", "código");
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text)
                .isEqualTo(receipt.output().message());
    }

    @Test
    void keepsTermsNotPresentedAfterAnInformativeTurnEvenWithASelectedService() {
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
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.NOT_PRESENTED);
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

        String expectedFirstResponse =
                "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Hermes handled the identity question";
        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(receipt.output().message()).isEqualTo(expectedFirstResponse);
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

        assertThat(identity.output().message())
                .isEqualTo("Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Hermes owns this conversation");
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
        assertThat(receipt.output().message())
                .isEqualTo("Olá! Sou a Urba, assistente virtual da Urbana do Brasil. not-json");
        assertThat(conversations.value.mode()).isEqualTo(br.com.urbana.connect.domain.reception.model.ReceptionMode.AI);
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text)
                .isEqualTo("Olá! Sou a Urba, assistente virtual da Urbana do Brasil. not-json");
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
    void proofTransfersToHumanAndOnlyApprovalRecordsThePaymentDecision() {
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

        assertThat(proof.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.BLOCKED_BY_HUMAN);
        assertThat(proof.output()).isNull();
        assertThat(conversations.value.mode()).isEqualTo(br.com.urbana.connect.domain.reception.model.ReceptionMode.HUMAN);
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement()
                .extracting(ReceptionMessage::text)
                .asString()
                .contains("Recebi o comprovante", "arquiteta")
                .doesNotContainIgnoringCase("briefing")
                .doesNotContainIgnoringCase("sistema")
                .doesNotContainIgnoringCase("exception");

        ReceptionOrchestrator.TurnReceipt approval = orchestrator.approvePaymentProof("poc:ana");
        ReceptionOrchestrator.TurnReceipt replay = orchestrator.approvePaymentProof("poc:ana");

        assertThat(approval.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.BLOCKED_BY_HUMAN);
        assertThat(approval.output()).isNull();
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(replay.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.BLOCKED_BY_HUMAN);
        assertThat(replay.output()).isNull();
        assertThat(sessions.chatCalls).isEqualTo(0);
        assertThat(sessions.lastInput).isNull();
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND)
                .hasSize(2);
        assertThat(transcript.messages).filteredOn(message ->
                message.senderType() == ReceptionMessageSender.HUMAN)
                .singleElement()
                .extracting(ReceptionMessage::text)
                .isEqualTo("Pagamento confirmado pela arquiteta.");
    }

    @Test
    void recordsBackendControlledHumanMessagesExactlyOnceAndExposesSafeControls() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW)
                .requestHumanHandoff("cliente pediu uma pessoa", NOW);
        MemoryTranscript transcript = new MemoryTranscript();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()), conversations,
                new MemoryFacts(), transcript, new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), Clock.fixed(NOW, ZoneOffset.UTC));

        ReceptionOrchestrator.HumanMessageReceipt first = orchestrator.recordHumanMessage(
                "poc:ana", "operator-message-1", "Decisão da arquiteta", NOW);
        ReceptionOrchestrator.HumanMessageReceipt replay = orchestrator.recordHumanMessage(
                "poc:ana", "operator-message-1", "texto diferente não substitui o original", NOW.plusSeconds(1));

        assertThat(first.duplicate()).isFalse();
        assertThat(replay.duplicate()).isTrue();
        assertThat(transcript.messages).filteredOn(message -> message.senderType() == ReceptionMessageSender.HUMAN)
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.text()).isEqualTo("Decisão da arquiteta");
                    assertThat(message.direction()).isEqualTo(ReceptionMessageDirection.OUTBOUND);
                });
        Map<String, Object> projection = orchestrator.projection("poc:ana");
        assertThat(projection).containsEntry("ownership", "HUMAN")
                .containsEntry("resumeStatus", ResumeStatus.NONE.name());
        Map<?, ?> controls = (Map<?, ?>) projection.get("controlAvailability");
        assertThat(controls.get("recordHumanMessage")).isEqualTo(true);
        assertThat(controls.get("returnToUrba")).isEqualTo(true);
    }

    @Test
    void synchronizesTheCompleteTypedBoundaryBeforeReturningToUrbaAndReplaysAsANoop() {
        MemoryConversation conversations = new MemoryConversation();
        ReceptionConversation human = ReceptionConversation.start("conversation-1", "poc:ana", NOW)
                .requestHumanHandoff("cliente pediu uma pessoa", NOW);
        conversations.value = human;
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("contact-message", "contact-event", "turn-1",
                human.id(), human.contactId(), ReceptionMessageDirection.INBOUND,
                ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT, "Olá", null, null, NOW));
        transcript.messages.add(new ReceptionMessage("urba-message", ReceptionEventIds.outbound("urba-1", "turn-1"),
                "turn-1", human.id(), human.contactId(), ReceptionMessageDirection.OUTBOUND,
                ReceptionMessageSender.URBA, ReceptionMessageType.TEXT, "Como posso ajudar?", null, null,
                NOW.plusSeconds(1)));
        transcript.messages.add(new ReceptionMessage("human-message", ReceptionEventIds.outbound("human-1", "operator-1"),
                "operator-1", human.id(), human.contactId(), ReceptionMessageDirection.OUTBOUND,
                ReceptionMessageSender.HUMAN, ReceptionMessageType.TEXT, "A arquiteta decidiu aguardar.", null, null,
                NOW.plusSeconds(2)));
        transcript.messages.add(new ReceptionMessage("system-message", ReceptionEventIds.outbound("system-1", "turn-1"),
                "turn-1", human.id(), human.contactId(), ReceptionMessageDirection.OUTBOUND,
                ReceptionMessageSender.SYSTEM, ReceptionMessageType.TEXT, "Decisão registrada.", null, null,
                NOW.plusSeconds(3)));
        MemoryFacts facts = new MemoryFacts();
        facts.values.add(CustomerFact.confirmed("poc:ana", "OCCUPATION", "DESIGNER",
                "contact-event", NOW));
        CapturingResumeGateway resume = new CapturingResumeGateway(HermesResumeGateway.Action.WAIT, null);
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()), conversations,
                facts, transcript, new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), Clock.fixed(NOW, ZoneOffset.UTC));

        ReceptionOrchestrator.ResumeReceipt first = orchestrator.returnToUrba(
                "poc:ana", "return-1", human.version(), resume);
        ReceptionOrchestrator.ResumeReceipt replay = orchestrator.returnToUrba(
                "poc:ana", "return-1", -1, resume);

        assertThat(first.status()).isEqualTo(ResumeStatus.COMPLETED);
        assertThat(first.ownership()).isEqualTo("URBA");
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.ownership()).isEqualTo("URBA");
        assertThat(resume.syncCalls).isEqualTo(1);
        assertThat(resume.decideCalls).isEqualTo(1);
        assertThat(resume.context.messages()).extracting(HermesResumeGateway.ContextMessage::senderType)
                .containsExactly("CONTACT", "URBA", "HUMAN", "SYSTEM");
        assertThat(resume.context.messages()).extracting(HermesResumeGateway.ContextMessage::sourceMessageId)
                .containsExactly("contact-event", transcript.messages.get(1).eventId(),
                        transcript.messages.get(2).eventId(), transcript.messages.get(3).eventId());
        assertThat(resume.context.facts()).singleElement().satisfies(fact -> {
            assertThat(fact.type()).isEqualTo("OCCUPATION");
            assertThat(fact.value()).isEqualTo("DESIGNER");
            assertThat(fact.confidence()).isEqualTo("CONFIRMED");
        });
        assertThat(resume.context.checksum())
                .isEqualTo("sha256:bd10553fa1a867f9461c8655037f8306e94845672e34a780c4696ae56d533416");
        assertThat(conversations.value.mode()).isEqualTo(br.com.urbana.connect.domain.reception.model.ReceptionMode.AI);
        assertThat(conversations.value.resumeStatus()).isEqualTo(ResumeStatus.COMPLETED);
        assertThat(transcript.messages).hasSize(4);
    }

    @Test
    void persistsResumeDecisionBeforeProactiveMessageAndFailsClosedOnGatewayError() {
        MemoryConversation conversations = new MemoryConversation();
        ReceptionConversation human = new ReceptionConversation("conversation-1", "poc:ana", ReceptionMode.HUMAN,
                CommercialStage.BRIEFING, "DECOR_INTERIORES", TermsStatus.ACCEPTED,
                PaymentStatus.CONFIRMED, "cliente pediu uma pessoa", NOW, NOW, 0);
        conversations.value = human;
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("contact-message", "contact-event", "turn-1",
                human.id(), human.contactId(), ReceptionMessageDirection.INBOUND,
                ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT, "Olá", null, null, NOW));
        transcript.beforeAppend = () -> assertThat(conversations.value.resumeStatus())
                .isEqualTo(ResumeStatus.COMPLETED);
        CapturingResumeGateway send = new CapturingResumeGateway(
                HermesResumeGateway.Action.SEND_MESSAGE, "Retomamos o atendimento por aqui.");
        send.nextStep = "BRIEFING";
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()), conversations,
                new MemoryFacts(), transcript, new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), Clock.fixed(NOW, ZoneOffset.UTC));

        ReceptionOrchestrator.ResumeReceipt sent = orchestrator.returnToUrba(
                "poc:ana", "return-send", human.version(), send);

        assertThat(sent.ownership()).isEqualTo("URBA");
        assertThat(transcript.messages).filteredOn(message -> message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text)
                .isEqualTo("Retomamos o atendimento por aqui.");

        MemoryConversation failedConversations = new MemoryConversation();
        ReceptionConversation failedHuman = ReceptionConversation.start("conversation-2", "poc:bia", NOW)
                .requestHumanHandoff("cliente pediu uma pessoa", NOW);
        failedConversations.value = failedHuman;
        MemoryTranscript failedTranscript = new MemoryTranscript();
        failedTranscript.messages.add(new ReceptionMessage("contact-message-2", "contact-event-2", "turn-2",
                failedHuman.id(), failedHuman.contactId(), ReceptionMessageDirection.INBOUND,
                ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT, "Olá", null, null, NOW));
        ReceptionOrchestrator failedOrchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()), failedConversations,
                new MemoryFacts(), failedTranscript, new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), Clock.fixed(NOW, ZoneOffset.UTC));
        CapturingResumeGateway unavailable = new CapturingResumeGateway(HermesResumeGateway.Action.WAIT, null);
        unavailable.failSync = true;

        ReceptionOrchestrator.ResumeReceipt safe = failedOrchestrator.returnToUrba(
                "poc:bia", "return-fail", failedHuman.version(), unavailable);

        assertThat(safe.ownership()).isEqualTo("HUMAN");
        assertThat(safe.status()).isEqualTo(ResumeStatus.FAILED_SAFE);
        assertThat(failedConversations.value.mode()).isEqualTo(
                br.com.urbana.connect.domain.reception.model.ReceptionMode.HUMAN);
        assertThat(failedTranscript.messages).hasSize(1);
    }

    @Test
    void keepsHumanOwnershipWhenHermesDoesNotAcknowledgeTheCompleteContext() {
        MemoryConversation conversations = new MemoryConversation();
        ReceptionConversation human = ReceptionConversation.start("conversation-incomplete", "poc:bia", NOW)
                .requestHumanHandoff("cliente pediu uma pessoa", NOW);
        conversations.value = human;
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("contact-message-incomplete", "contact-event-incomplete",
                "turn-incomplete", human.id(), human.contactId(), ReceptionMessageDirection.INBOUND,
                ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT, "Olá", null, null, NOW));
        CapturingResumeGateway incomplete = new CapturingResumeGateway(HermesResumeGateway.Action.WAIT, null);
        incomplete.incompleteReceipt = true;
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()), conversations,
                new MemoryFacts(), transcript, new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), Clock.fixed(NOW, ZoneOffset.UTC));

        ReceptionOrchestrator.ResumeReceipt receipt = orchestrator.returnToUrba(
                "poc:bia", "return-incomplete", human.version(), incomplete);

        assertThat(receipt.ownership()).isEqualTo("HUMAN");
        assertThat(receipt.status()).isEqualTo(ResumeStatus.FAILED_SAFE);
        assertThat(incomplete.decideCalls).isZero();
        assertThat(conversations.value.mode()).isEqualTo(ReceptionMode.HUMAN);
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
                && fact.value().equals("DECOR_INTERIORES")
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
        assertThat(duplicate.output().message())
                .isEqualTo("Olá! Sou a Urba, assistente virtual da Urbana do Brasil. ok");
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
        assertThat(receipt.output().message())
                .isEqualTo("Olá! Sou a Urba, assistente virtual da Urbana do Brasil. recovered");
        assertThat(sessions.chatCalls).isEqualTo(1);
        assertThat(transcript.messages).extracting(ReceptionMessage::direction)
                .containsExactly(ReceptionMessageDirection.INBOUND, ReceptionMessageDirection.OUTBOUND);
        assertThat(turns.values.values()).singleElement()
                .extracting(ReceptionTurn::status)
                .isEqualTo(ReceptionTurnStatus.COMPLETED);
    }

    @Test
    void allowsOperatorPaymentApprovalAfterHumanHandoffWithoutReactivatingAutomation() {
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

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.BLOCKED_BY_HUMAN);
        assertThat(receipt.output()).isNull();
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

    @Test
    void acceptsAnExplicitInboundOnlyAfterDurableTermsPresentationEvidence() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        ReceptionConversation presented = policy.presentTerms(
                policy.selectService(ReceptionConversation.start("conversation-1", "poc:ana", NOW), "DECOR", NOW),
                List.of(), NOW);
        presented = presented.bindContractingUnit("unit-1", "sala", "environment-event", NOW);
        presented = policy.presentTerms(policy.selectService(presented, "DECOR", NOW), List.of(), NOW);
        presented = presented.activateTermsConsent("presentation-1", NOW);
        conversations.value = presented;
        MemoryAudits audits = new MemoryAudits();
        audits.savePresentationIfAbsent(new TermsConsentAudit("presentation-1", presented.id(), presented.contactId(),
                "turn-presentation", presented.contractingUnitId(), presented.environmentLabel(),
                presented.environmentSourceMessageId(), presented.selectedService(), policy.termsUrl("DECOR"), "v1",
                "invocation-1", "terms-outbound", NOW, null, null, null, null, NOW,
                TermsConsentStatus.PRESENTED, presented.version(), null));
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("Obrigada!"), new MemoryLinks()), conversations,
                new MemoryFacts(), transcript, turns, policy, new ReceptionTurnCoordinator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        orchestrator.setTermsAcceptanceUseCase(new TermsAcceptanceUseCase(audits, conversations));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "acceptance-event", "poc:ana", ReceptionMessageType.TEXT, "Aceito os termos", NOW.plusSeconds(1)));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.ACCEPTED);
        assertThat(audits.findByPresentationId("presentation-1")).hasValueSatisfying(audit -> {
            assertThat(audit.status()).isEqualTo(TermsConsentStatus.ACCEPTED);
            assertThat(audit.acceptanceEventId()).isEqualTo("acceptance-event");
            assertThat(audit.acceptanceTextExact()).isEqualTo("Aceito os termos");
        });
    }

    @Test
    void recordsTermsPresentationOnlyWhenSuccessfulInvocationResourceAppearsInPublishedText() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryAudits audits = new MemoryAudits();
        MemoryInvocations invocations = new MemoryInvocations();
        DomainToolInvocation invocation = new DomainToolInvocation("invocation-1", "key-1", "any-turn", "session-1",
                "poc:ana", DomainToolName.PREPARE_TERMS, "hash", DomainToolInvocationStatus.SUCCEEDED, "OK",
                Map.of("url", policy.termsUrl("DECOR")), NOW, NOW.plusSeconds(1));
        invocations.values.add(invocation);
        MemoryConversation conversations = new MemoryConversation();
        ReceptionConversation presented = ReceptionConversation.start("conversation-1", "poc:ana", NOW)
                .bindContractingUnit("unit-1", "sala", "environment-event", NOW)
                .selectService("DECOR_INTERIORES", NOW)
                .presentTerms(NOW.plusSeconds(1));
        conversations.value = presented;
        MemoryTranscript transcript = new MemoryTranscript();
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("Confira os termos: " + policy.termsUrl("DECOR")),
                        new MemoryLinks()), conversations, new MemoryFacts(), transcript, new MemoryTurns(), policy,
                new ReceptionTurnCoordinator(), null, invocations, Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));
        orchestrator.setTermsAcceptanceUseCase(new TermsAcceptanceUseCase(audits, conversations));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "terms-event", "poc:ana", ReceptionMessageType.TEXT, "Quero ver os termos", NOW.plusSeconds(2)));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        assertThat(conversations.value.activeTermsConsentId()).isNotBlank();
        assertThat(audits.findByPresentationId(conversations.value.activeTermsConsentId()))
                .hasValueSatisfying(value -> assertThat(value.status()).isEqualTo(TermsConsentStatus.PRESENTED));
    }

    @Test
    void keepsTermsUnactivatedWhenInvocationIsMissingOrResourceIsNotVisible() {
        CommercialPolicyService policy = new CommercialPolicyService();
        for (List<DomainToolInvocation> ledger : List.of(List.<DomainToolInvocation>of(), List.of(new DomainToolInvocation(
                "invocation-failed", "key-failed", "any-turn", "session-1", "poc:ana",
                DomainToolName.PREPARE_TERMS, "hash", DomainToolInvocationStatus.FAILED, "FAILED", Map.of(
                        "url", policy.termsUrl("DECOR")), NOW, NOW.plusSeconds(1))), List.of(new DomainToolInvocation(
                "invocation-no-url", "key-no-url", "any-turn", "session-1", "poc:ana",
                DomainToolName.PREPARE_TERMS, "hash", DomainToolInvocationStatus.SUCCEEDED, "OK", Map.of(), NOW,
                NOW.plusSeconds(1))))) {
            MemoryConversation conversations = new MemoryConversation();
            conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW)
                    .bindContractingUnit("unit-1", "sala", "environment-event", NOW)
                    .selectService("DECOR_INTERIORES", NOW)
                    .presentTerms(NOW.plusSeconds(1));
            MemoryAudits audits = new MemoryAudits();
            MemoryInvocations invocations = new MemoryInvocations();
            invocations.values.addAll(ledger);
            String response = "Confira os termos, mas sem o endereço";
            MemoryTranscript transcript = new MemoryTranscript();
            ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                    new HermesSessionService(new FakeSessions(response), new MemoryLinks()), conversations,
                    new MemoryFacts(), transcript, new MemoryTurns(), policy, new ReceptionTurnCoordinator(), null,
                    invocations, Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));
            orchestrator.setTermsAcceptanceUseCase(new TermsAcceptanceUseCase(audits, conversations));

            orchestrator.process(new InboundConversationEvent("terms-noop-" + ledger.size(), "poc:ana",
                    ReceptionMessageType.TEXT, "termos", NOW.plusSeconds(2)));

            assertThat(conversations.value.activeTermsConsentId()).isNull();
            assertThat(audits.values).isEmpty();
        }
    }

    @Test
    void fencesAnObsoleteInboundWhenANewerMessageArrivesBeforePublication() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryTurns turns = new MemoryTurns();
        FakeSessions sessions = new FakeSessions("Resposta tardia");
        sessions.beforeResponse = () -> transcript.messages.add(new ReceptionMessage("newer-message", "newer-event",
                "newer-correlation", "conversation-1", "poc:ana", ReceptionMessageDirection.INBOUND,
                ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT, "mensagem nova", null, null,
                NOW.plusSeconds(2)));
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(sessions, new MemoryLinks()), conversations, new MemoryFacts(), transcript,
                turns, new CommercialPolicyService(), new ReceptionTurnCoordinator(), Clock.fixed(NOW, ZoneOffset.UTC));

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(new InboundConversationEvent(
                "old-event", "poc:ana", ReceptionMessageType.TEXT, "oi", NOW));

        assertThat(receipt.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.FAILED_SAFE_TO_RETRY);
        assertThat(receipt.error()).isEqualTo("STALE_INBOUND_BEFORE_PUBLICATION");
        assertThat(transcript.messages).filteredOn(message -> message.direction() == ReceptionMessageDirection.OUTBOUND)
                .isEmpty();
        assertThat(turns.values.values()).singleElement().extracting(ReceptionTurn::failureCode)
                .isEqualTo("STALE_INBOUND_BEFORE_PUBLICATION");
    }

    @Test
    void validatesBatchPresenceAndContactOwnershipBeforePersistingAnything() {
        ReceptionOrchestrator orchestrator = new ReceptionOrchestrator(
                new HermesSessionService(new FakeSessions("unused"), new MemoryLinks()), new MemoryConversation(),
                new MemoryFacts(), new MemoryTranscript(), new MemoryTurns(), new CommercialPolicyService(),
                new ReceptionTurnCoordinator(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> orchestrator.processBatch(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orchestrator.processBatch(List.of())).isInstanceOf(IllegalArgumentException.class);
        InboundConversationEvent first = new InboundConversationEvent("batch-a", "poc:ana", ReceptionMessageType.TEXT,
                "oi", NOW);
        InboundConversationEvent second = new InboundConversationEvent("batch-b", "poc:bia", ReceptionMessageType.TEXT,
                "oi", NOW);
        assertThatThrownBy(() -> orchestrator.processBatch(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("multiple contacts");
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

    private static final class CapturingResumeGateway implements HermesResumeGateway {
        private final HermesResumeGateway.Action action;
        private final String message;
        int syncCalls;
        int decideCalls;
        boolean failSync;
        boolean incompleteReceipt;
        String nextStep = "NONE";
        ResumeContext context;

        private CapturingResumeGateway(HermesResumeGateway.Action action, String message) {
            this.action = action;
            this.message = message;
        }

        @Override
        public ContextSyncReceipt synchronize(String sessionId, ResumeContext context) {
            syncCalls++;
            this.context = context;
            if (failSync) {
                throw new IllegalStateException("provider failure must not cross boundary");
            }
            return new ContextSyncReceipt(context.resumeId(), context.lineageId(), sessionId,
                    context.checksum(), context.cursor(), incompleteReceipt
                            ? Math.max(0, context.watermark() - 1) : context.watermark());
        }

        @Override
        public ResumeDecision decide(String sessionId, ResumeCommand command) {
            decideCalls++;
            return new ResumeDecision(command.resumeId(), sessionId, action, nextStep, message,
                    context.messages().stream().map(ContextMessage::sourceMessageId).toList(),
                    action.name(), 1.0);
        }
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
        final List<CustomerFact> values = new ArrayList<>();
        @Override public List<CustomerFact> findCurrentByContactId(String contactId, Instant at) {
            findCalls++;
            return values.stream().filter(fact -> fact.contactId().equals(contactId)
                    && fact.isCurrentAt(at)).toList();
        }
        @Override public List<CustomerFact> findByContactId(String contactId) {
            findCalls++;
            return values.stream().filter(fact -> fact.contactId().equals(contactId)).toList();
        }
        @Override public CustomerFact save(CustomerFact fact) { saveCalls++; return fact; }
    }

    private static final class MemoryAudits implements TermsConsentAuditGateway {
        final Map<String, TermsConsentAudit> values = new HashMap<>();

        @Override public Optional<TermsConsentAudit> findByPresentationId(String presentationId) {
            return Optional.ofNullable(values.get(presentationId));
        }

        @Override public Optional<TermsConsentAudit> findPresented(String conversationId, String unitId) {
            return values.values().stream().filter(value -> value.conversationId().equals(conversationId)
                    && value.contractingUnitId().equals(unitId)
                    && value.status() == TermsConsentStatus.PRESENTED).findFirst();
        }

        @Override public TermsConsentAudit savePresentationIfAbsent(TermsConsentAudit audit) {
            return values.computeIfAbsent(audit.presentationId(), ignored -> audit);
        }

        @Override public TermsConsentAudit acceptIfPresented(String presentationId, String eventId,
                                                              String messageId, String text, long version, Instant at) {
            TermsConsentAudit current = values.get(presentationId);
            if (current == null) throw new IllegalStateException("missing presentation");
            TermsConsentAudit accepted = current.accept(messageId, eventId, text, at, version);
            values.put(presentationId, accepted);
            return accepted;
        }
    }

    private static final class MemoryInvocations implements DomainToolInvocationGateway {
        final List<DomainToolInvocation> values = new ArrayList<>();

        @Override public Optional<DomainToolInvocation> findByIdempotencyKey(String key) {
            return values.stream().filter(value -> value.idempotencyKey().equals(key)).findFirst();
        }

        @Override public DomainToolInvocation save(DomainToolInvocation invocation) {
            values.add(invocation);
            return invocation;
        }

        @Override public List<DomainToolInvocation> findByTurnId(String turnId) {
            return List.copyOf(values);
        }
    }

    private static final class MemoryTranscript implements ReceptionTranscriptGateway {
        final List<ReceptionMessage> messages = new ArrayList<>();
        Runnable beforeAppend = () -> { };
        @Override public boolean appendIfAbsent(ReceptionMessage message) {
            if (findByEventId(message.eventId()).isPresent()) return false;
            beforeAppend.run();
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
