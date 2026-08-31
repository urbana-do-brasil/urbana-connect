package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.reception.tools.StatefulDomainToolService;
import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.CommercialStage;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.model.TermsConsentAudit;
import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocationStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway.HermesHistorySnapshot;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        assertThat(service.reconcile("turn-1")).hasValueSatisfying(value ->
                assertThat(value).contains("resposta tardia"));
        assertThat(service.reconcile("turn-1")).isEmpty();
        assertThat(transcript.messages).filteredOn(message -> message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text)
                .satisfies(value -> assertThat(value).contains("resposta tardia"));
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

        assertThat(service.reconcile("turn-3")).hasValueSatisfying(value ->
                assertThat(value).contains("resposta tardia"));
        verify(gateway).revoke(eq("session-1"), eq("turn-3"), any(Instant.class));
        assertThat(transcript.messages).filteredOn(message ->
                message.direction() == ReceptionMessageDirection.OUTBOUND).hasSize(1);
    }

    @Test
    void handoffAckWinsOverLateHermesTextDuringReconciliation() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn turn = ReceptionTurn.queued("turn-human", "corr-human", "poc:ana", "session-1",
                        List.of("message-human"), NOW, "cursor-1|1")
                .start(NOW)
                .reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", NOW.plusSeconds(1));
        turns.save(turn);
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-human", "poc:ana", NOW)
                .requestHumanHandoff("cliente pediu uma pessoa", NOW.plusSeconds(1));
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("message-human", "event-human", "corr-human",
                "conversation-human", "poc:ana", ReceptionMessageDirection.INBOUND,
                ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT, "quero uma pessoa", null, null, NOW));
        HermesSessionsGateway sessions = new HermesSessionsGateway() {
            @Override public String createSession(String contactId) { return "session-1"; }
            @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) {
                throw new AssertionError("must not dispatch");
            }
            @Override public List<HermesHistoryMessage> history(String sessionId) { return List.of(); }
            @Override public HermesHistorySnapshot historySnapshot(String sessionId) {
                return new HermesHistorySnapshot("cursor-2", List.of(
                        new HermesHistoryMessage("user", "quero uma pessoa"),
                        new HermesHistoryMessage("assistant", "o sistema está indisponível; tente retry")));
            }
        };
        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(sessions, new EmptyLinks()), conversations, transcript, turns,
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));

        assertThat(service.reconcile("turn-human"))
                .contains("Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.");
        assertThat(turns.value.status().name()).isEqualTo("BLOCKED_BY_HUMAN");
        assertThat(transcript.messages).noneMatch(message -> message.text() != null
                && message.text().contains("sistema está indisponível"));
        assertThat(transcript.messages).filteredOn(message -> message.senderType() == ReceptionMessageSender.URBA)
                .singleElement().extracting(ReceptionMessage::text)
                .isEqualTo("Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.");
    }

    @Test
    void reconciledTermsTextIsPublishedOnlyWithDurablePresentationEvidence() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn turn = ReceptionTurn.queued("turn-terms", "corr-terms", "poc:ana", "session-1",
                List.of("message-terms"), NOW, "cursor-1|1")
                .start(NOW).reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", NOW.plusSeconds(1));
        turns.save(turn);
        MemoryConversation conversations = new MemoryConversation();
        ReceptionConversation presented = ReceptionConversation.start("conversation-terms", "poc:ana", NOW)
                .bindContractingUnit("unit-1", "sala", "message-environment", NOW)
                .selectService("DECOR_INTERIORES", NOW)
                .presentTerms(NOW);
        conversations.value = presented;
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("message-terms", "event-terms", "corr-terms",
                "conversation-terms", "poc:ana", ReceptionMessageDirection.INBOUND,
                ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT, "aguardo os termos", null, null, NOW));
        CommercialPolicyService policy = new CommercialPolicyService();
        String termsUrl = policy.termsUrl("DECOR_INTERIORES");
        MemoryAudits audits = new MemoryAudits();
        DomainToolInvocation invocation = new DomainToolInvocation("invocation-terms", "key-terms", "turn-terms",
                "session-1", "poc:ana", DomainToolName.PREPARE_TERMS, "hash", DomainToolInvocationStatus.SUCCEEDED,
                "OK", Map.of("status", "PRESENTED", "url", termsUrl), NOW, NOW.plusSeconds(1));
        MemoryInvocations invocations = new MemoryInvocations(invocation);
        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(new TermsSessions(termsUrl), new EmptyLinks()), conversations, transcript,
                turns, Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC),
                new ReceptionTurnReconciliationService.Dependencies(null, policy,
                        new TermsAcceptanceUseCase(audits, conversations), invocations));

        assertThat(service.reconcile("turn-terms")).hasValueSatisfying(value ->
                assertThat(value).contains(termsUrl));
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
        assertThat(conversations.value.activeTermsConsentId()).isNotBlank();
        assertThat(audits.findByPresentationId(conversations.value.activeTermsConsentId()))
                .hasValueSatisfying(audit -> assertThat(audit.status()).isEqualTo(TermsConsentStatus.PRESENTED));
    }

    @Test
    void invalidPreparedPaymentOutputFallsBackToPocQuantityAndProofGuidance() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn turn = ReceptionTurn.queued("turn-payment", "corr-payment", "poc:ana", "session-1",
                        List.of("message-payment"), NOW, "cursor-1|1")
                .start(NOW)
                .reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", NOW.plusSeconds(1));
        turns.save(turn);
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = new ReceptionConversation("conversation-payment", "poc:ana", ReceptionMode.AI,
                CommercialStage.PAYMENT, "DECOR_INTERIORES", TermsStatus.ACCEPTED, PaymentStatus.PREPARED,
                null, NOW, NOW, 1);
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("message-payment", "event-payment", "corr-payment",
                "conversation-payment", "poc:ana", ReceptionMessageDirection.INBOUND,
                ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT, "Como faço o pagamento?",
                null, null, NOW));
        HermesSessionsGateway sessions = new HermesSessionsGateway() {
            @Override public String createSession(String contactId) { return "session-1"; }
            @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) {
                throw new AssertionError("must not dispatch");
            }
            @Override public List<HermesHistoryMessage> history(String sessionId) { return List.of(); }
            @Override public HermesHistorySnapshot historySnapshot(String sessionId) {
                return new HermesHistorySnapshot("cursor-2", List.of(
                        new HermesHistoryMessage("user", "Como faço o pagamento?"),
                        new HermesHistoryMessage("assistant",
                                "{\"message\":\"Pague agora.\",\"nextAction\":\"AWAIT_PAYMENT_PROOF\"}")));
            }
        };
        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(sessions, new EmptyLinks()), conversations, transcript, turns,
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC),
                new ReceptionTurnReconciliationService.Dependencies(null, new CommercialPolicyService(), null, null));

        assertThat(service.reconcile("turn-payment")).hasValueSatisfying(value -> assertThat(value)
                .contains("simulação", "1 serviço para cada ambiente contratado", "comprovante")
                .doesNotContain("Pague agora", "http"));
        assertThat(transcript.messages).filteredOn(message -> message.direction() == ReceptionMessageDirection.OUTBOUND)
                .singleElement().extracting(ReceptionMessage::text)
                .satisfies(value -> assertThat(value)
                        .contains("simulação", "1 serviço para cada ambiente contratado", "comprovante"));
    }

    @Test
    void ignoresUnknownOrAlreadyResolvedTurnReferencesAndMalformedCheckpoints() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn completed = ReceptionTurn.queued("turn-done", "corr-done", "poc:ana", "session-1",
                List.of("message-done"), NOW, "cursor-1|1").start(NOW).complete(AgentUsage.empty(), NOW.plusSeconds(1),
                new br.com.urbana.connect.domain.reception.model.AgentOutput("ok",
                        br.com.urbana.connect.domain.reception.model.AgentNextAction.AWAIT_CUSTOMER));
        turns.save(completed);
        ReceptionTurn malformed = ReceptionTurn.queued("turn-malformed", "corr-malformed", "poc:ana", "session-1",
                List.of("message-malformed"), NOW, "not-a-checkpoint").start(NOW)
                .reconcile("HERMES_TIMEOUT_AFTER_DISPATCH", NOW.plusSeconds(1));
        turns.save(malformed);
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(new ReceptionMessage("message-event", "event-malformed", "corr-malformed",
                "conversation-1", "poc:ana", ReceptionMessageDirection.INBOUND, ReceptionMessageSender.CONTACT,
                ReceptionMessageType.TEXT, "oi", null, null, NOW));

        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(new EmptyLinksSessions(), new EmptyLinks()), new MemoryConversation(),
                transcript, turns, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.reconcile("missing")).isEmpty();
        assertThat(service.reconcile("turn-done")).isEmpty();
        assertThat(service.reconcile("event-malformed")).isEmpty();
        assertThat(service.reconcile("turn-malformed")).isEmpty();
    }

    @Test
    void leavesReconcilingTurnWhenCursorIsUnchangedTooShortOrOutputIsInvalid() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn turn = ReceptionTurn.queued("turn-nochange", "corr-nochange", "poc:ana", "session-1",
                List.of("message-1"), NOW, "cursor-1|1").start(NOW).reconcile("timeout", NOW.plusSeconds(1));
        turns.save(turn);
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(inbound("message-1", "conversation-1", "Oi", NOW));
        SnapshotSessions sessions = new SnapshotSessions("cursor-1", List.of(inboundHistory(), assistant("não json")));
        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(sessions, new EmptyLinks()), conversations, transcript, turns,
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));

        assertThat(service.reconcile("turn-nochange")).isEmpty();
        sessions.cursor = "cursor-2";
        sessions.messages = List.of(inboundHistory());
        assertThat(service.reconcile("turn-nochange")).isEmpty();
        sessions.messages = List.of(inboundHistory(), assistant(""));
        assertThat(service.reconcile("turn-nochange")).isEmpty();
        assertThat(turns.value.status()).isEqualTo(ReceptionTurnStatus.RECONCILING);
    }

    @Test
    void appliesEveryPaymentFallbackWithoutConfirmingOrReleasingBriefing() {
        for (PaymentStatus paymentStatus : PaymentStatus.values()) {
            MemoryTurns turns = new MemoryTurns();
            ReceptionTurn turn = ReceptionTurn.queued("turn-" + paymentStatus, "corr-" + paymentStatus,
                    "poc:ana", "session-1", List.of("message-" + paymentStatus), NOW, "cursor-1|1")
                    .start(NOW).reconcile("timeout", NOW.plusSeconds(1));
            turns.save(turn);
            MemoryConversation conversations = new MemoryConversation();
            conversations.value = paymentConversation(paymentStatus);
            MemoryTranscript transcript = new MemoryTranscript();
            transcript.messages.add(inbound("message-" + paymentStatus, "conversation-1", "Como faço?", NOW));
            SnapshotSessions sessions = new SnapshotSessions("cursor-2", List.of(inboundHistory(),
                    assistant(paymentStatus == PaymentStatus.PROOF_RECEIVED
                            ? "{\"message\":\"aguarde\",\"nextAction\":\"AWAIT_PAYMENT_PROOF\"}"
                            : "{\"message\":\"Pagamento confirmado\",\"nextAction\":\"AWAIT_PAYMENT_APPROVAL\"}")));
            ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                    new HermesSessionService(sessions, new EmptyLinks()), conversations, transcript, turns,
                    Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC),
                    new ReceptionTurnReconciliationService.Dependencies(null,
                            new CommercialPolicyService(), null, null));

            assertThat(service.reconcile(turn.id())).isPresent();
            assertThat(turns.value.status()).isEqualTo(ReceptionTurnStatus.COMPLETED);
            String message = transcript.messages.stream().filter(value ->
                    value.direction() == ReceptionMessageDirection.OUTBOUND).findFirst().orElseThrow().text();
            switch (paymentStatus) {
                case PROOF_RECEIVED -> assertThat(message).contains("aguarda validação humana");
                case PREPARED -> assertThat(message).contains("1 serviço para cada ambiente");
                case NOT_STARTED -> assertThat(message).contains("escolha uma forma de pagamento");
                case REJECTED -> assertThat(message).containsIgnoringCase("não consigo confirmar");
                case CONFIRMED -> assertThat(message).contains("Pagamento confirmado");
            }
        }
    }

    @Test
    void fencesNewInboundBeforeAndDuringReconciliationPublication() {
        for (boolean duringPublication : List.of(false, true)) {
            MemoryTurns turns = new MemoryTurns();
            ReceptionTurn turn = ReceptionTurn.queued("turn-fence-" + duringPublication,
                    "corr-fence-" + duringPublication, "poc:ana", "session-1",
                    List.of("message-fence"), NOW, "cursor-1|1").start(NOW)
                    .reconcile("timeout", NOW.plusSeconds(1));
            turns.save(turn);
            MemoryConversation conversations = new MemoryConversation();
            conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW);
            MemoryTranscript transcript = new MemoryTranscript();
            transcript.messages.add(inbound("message-fence", "conversation-1", "Oi", NOW));
            if (!duringPublication) {
                transcript.messages.add(inbound("newer-before", "conversation-1", "Ainda estou aqui", NOW.plusSeconds(2)));
            } else {
                transcript.beforeAppend = () -> transcript.messages.add(inbound("newer-during", "conversation-1",
                        "Mensagem nova", NOW.plusSeconds(2)));
            }
            SnapshotSessions sessions = new SnapshotSessions("cursor-2", List.of(inboundHistory(),
                    assistant("{\"message\":\"resposta tardia\",\"nextAction\":\"AWAIT_CUSTOMER\"}")));
            ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                    new HermesSessionService(sessions, new EmptyLinks()), conversations, transcript, turns,
                    Clock.fixed(NOW.plusSeconds(3), ZoneOffset.UTC));

            assertThat(service.reconcile(turn.id())).isEmpty();
            assertThat(turns.value.status()).isEqualTo(ReceptionTurnStatus.FAILED_SAFE_TO_RETRY);
            assertThat(turns.value.failureClass()).isEqualTo(duringPublication
                    ? "STALE_INBOUND_DURING_RECONCILIATION_PUBLICATION" : "STALE_INBOUND_BEFORE_RECONCILIATION");
        }
    }

    @Test
    void releasesLeaseWhenHandoffAckAlreadyExistsAndWhenConversationChangesDuringSnapshot() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn turn = ReceptionTurn.queued("turn-handoff-existing", "corr-handoff-existing", "poc:ana",
                "session-1", List.of("message-handoff-existing"), NOW, "cursor-1|1").start(NOW)
                .reconcile("timeout", NOW.plusSeconds(1));
        turns.save(turn);
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-1", "poc:ana", NOW)
                .requestHumanHandoff("cliente pediu pessoa", NOW.plusSeconds(1));
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(inbound("message-handoff-existing", "conversation-1", "pessoa", NOW));
        String ackId = StatefulDomainToolService.handoffAckEventId(conversations.value);
        transcript.messages.add(new ReceptionMessage("ack", ackId, "corr-handoff-existing", "conversation-1", "poc:ana",
                ReceptionMessageDirection.OUTBOUND, ReceptionMessageSender.URBA, ReceptionMessageType.TEXT,
                StatefulDomainToolService.HUMAN_HANDOFF_ACK, null, null, NOW.plusSeconds(1)));
        ActiveTurnLeaseGateway leaseGateway = mock(ActiveTurnLeaseGateway.class);
        ActiveTurnLeaseService leases = new ActiveTurnLeaseService(leaseGateway, Clock.fixed(NOW, ZoneOffset.UTC),
                java.time.Duration.ofSeconds(30));
        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(new EmptyLinksSessions(), new EmptyLinks()), conversations, transcript, turns,
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC), leases);

        assertThat(service.reconcile(turn.id())).contains(StatefulDomainToolService.HUMAN_HANDOFF_ACK);
        verify(leaseGateway).revoke(eq("session-1"), eq(turn.id()), any(Instant.class));
        assertThat(transcript.messages).filteredOn(value -> value.eventId().equals(ackId)).hasSize(1);

        MemoryTurns changedTurns = new MemoryTurns();
        ReceptionTurn changedTurn = ReceptionTurn.queued("turn-mode-race", "corr-mode-race", "poc:ana", "session-2",
                List.of("message-mode-race"), NOW, "cursor-1|1").start(NOW)
                .reconcile("timeout", NOW.plusSeconds(1));
        changedTurns.save(changedTurn);
        MemoryConversation changedConversations = new MemoryConversation();
        changedConversations.value = ReceptionConversation.start("conversation-2", "poc:ana", NOW);
        MemoryTranscript changedTranscript = new MemoryTranscript();
        changedTranscript.messages.add(inbound("message-mode-race", "conversation-2", "oi", NOW));
        SnapshotSessions changingSessions = new SnapshotSessions("cursor-2", List.of(inboundHistory(), assistant(
                "{\"message\":\"late\",\"nextAction\":\"AWAIT_CUSTOMER\"}")));
        changingSessions.beforeSnapshot = () -> changedConversations.value = changedConversations.value
                .requestHumanHandoff("cliente pediu pessoa", NOW.plusSeconds(2));
        ReceptionTurnReconciliationService changingService = new ReceptionTurnReconciliationService(
                new HermesSessionService(changingSessions, new EmptyLinks()), changedConversations, changedTranscript,
                changedTurns, Clock.fixed(NOW.plusSeconds(3), ZoneOffset.UTC));

        assertThat(changingService.reconcile(changedTurn.id())).contains(StatefulDomainToolService.HUMAN_HANDOFF_ACK);
        assertThat(changedTurns.value.status()).isEqualTo(ReceptionTurnStatus.BLOCKED_BY_HUMAN);
    }

    @Test
    void preservesIdentityOnlyForTheFirstReconciledOutbound() {
        MemoryTurns turns = new MemoryTurns();
        ReceptionTurn turn = ReceptionTurn.queued("turn-identity", "corr-identity", "poc:ana", "session-1",
                List.of("message-identity"), NOW, "cursor-1|1").start(NOW).reconcile("timeout", NOW.plusSeconds(1));
        turns.save(turn);
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("conversation-identity", "poc:ana", NOW);
        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.add(inbound("message-identity", "conversation-identity", "Oi", NOW));
        transcript.messages.add(new ReceptionMessage("old-outbound", "old-event", "old-corr", "conversation-identity",
                "poc:ana", ReceptionMessageDirection.OUTBOUND, ReceptionMessageSender.URBA, ReceptionMessageType.TEXT,
                "mensagem anterior", null, null, NOW.plusSeconds(1)));
        SnapshotSessions sessions = new SnapshotSessions("cursor-2", List.of(inboundHistory(), assistant(
                "{\"message\":\"resposta\",\"nextAction\":\"AWAIT_CUSTOMER\"}")));
        ReceptionTurnReconciliationService service = new ReceptionTurnReconciliationService(
                new HermesSessionService(sessions, new EmptyLinks()), conversations, transcript, turns,
                Clock.fixed(NOW.plusSeconds(3), ZoneOffset.UTC));

        assertThat(service.reconcile(turn.id())).hasValue("resposta");
        assertThat(transcript.messages).filteredOn(value -> value.direction() == ReceptionMessageDirection.OUTBOUND)
                .extracting(ReceptionMessage::text).containsExactly("mensagem anterior", "resposta");
    }

    private static ReceptionMessage inbound(String id, String conversationId, String text, Instant at) {
        return new ReceptionMessage(id, "event-" + id, "corr-" + id, conversationId, "poc:ana",
                ReceptionMessageDirection.INBOUND, ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT,
                text, null, null, at);
    }

    private static HermesSessionsGateway.HermesHistoryMessage inboundHistory() {
        return new HermesSessionsGateway.HermesHistoryMessage("user", "Oi");
    }

    private static HermesSessionsGateway.HermesHistoryMessage assistant(String content) {
        return new HermesSessionsGateway.HermesHistoryMessage("assistant", content);
    }

    private static ReceptionConversation paymentConversation(PaymentStatus status) {
        return new ReceptionConversation("conversation-1", "poc:ana", ReceptionMode.AI, CommercialStage.PAYMENT,
                "DECOR_INTERIORES", TermsStatus.ACCEPTED, status, null, NOW, NOW, 1);
    }

    private static final class SnapshotSessions implements HermesSessionsGateway {
        private String cursor;
        private List<HermesHistoryMessage> messages;
        private Runnable beforeSnapshot = () -> { };

        private SnapshotSessions(String cursor, List<HermesHistoryMessage> messages) {
            this.cursor = cursor;
            this.messages = messages;
        }

        @Override public String createSession(String contactId) { return "session-1"; }
        @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) { throw new AssertionError(); }
        @Override public List<HermesHistoryMessage> history(String sessionId) { return messages; }
        @Override public HermesHistorySnapshot historySnapshot(String sessionId) {
            beforeSnapshot.run();
            return new HermesHistorySnapshot(cursor, messages);
        }
    }

    private static final class EmptyLinksSessions implements HermesSessionsGateway {
        @Override public String createSession(String contactId) { return "session-1"; }
        @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) { throw new AssertionError(); }
        @Override public List<HermesHistoryMessage> history(String sessionId) { return List.of(); }
    }

    private static final class TermsSessions implements HermesSessionsGateway {
        private final String termsUrl;
        private TermsSessions(String termsUrl) { this.termsUrl = termsUrl; }
        @Override public String createSession(String contactId) { return "session-1"; }
        @Override public HermesChatResult chat(String sessionId, HermesChatRequest request) { throw new AssertionError(); }
        @Override public List<HermesHistoryMessage> history(String sessionId) { return List.of(); }
        @Override public HermesHistorySnapshot historySnapshot(String sessionId) {
            return new HermesHistorySnapshot("cursor-2", List.of(
                    new HermesHistoryMessage("user", "aguardo os termos"),
                    new HermesHistoryMessage("assistant", "{\"message\":\"Confira os termos: "
                            + termsUrl + "\",\"nextAction\":\"AWAIT_CUSTOMER\"}")));
        }
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
        Runnable beforeAppend = () -> { };
        @Override public boolean appendIfAbsent(ReceptionMessage message) {
            if (messages.stream().anyMatch(item -> item.eventId().equals(message.eventId()))) return false;
            beforeAppend.run();
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

    private static final class MemoryAudits implements TermsConsentAuditGateway {
        private final Map<String, TermsConsentAudit> values = new HashMap<>();
        @Override public Optional<TermsConsentAudit> findByPresentationId(String id) {
            return Optional.ofNullable(values.get(id));
        }
        @Override public Optional<TermsConsentAudit> findPresented(String conversationId, String unitId) {
            return values.values().stream().filter(value -> value.conversationId().equals(conversationId)
                    && value.contractingUnitId().equals(unitId)
                    && value.status() == TermsConsentStatus.PRESENTED).findFirst();
        }
        @Override public TermsConsentAudit savePresentationIfAbsent(TermsConsentAudit audit) {
            return values.computeIfAbsent(audit.presentationId(), ignored -> audit);
        }
        @Override public TermsConsentAudit acceptIfPresented(String id, String eventId, String messageId,
                                                             String text, long version, Instant at) {
            TermsConsentAudit current = findByPresentationId(id).orElseThrow();
            TermsConsentAudit accepted = current.accept(messageId, eventId, text, at, version);
            values.put(id, accepted);
            return accepted;
        }
    }

    private static final class MemoryInvocations implements DomainToolInvocationGateway {
        private final DomainToolInvocation invocation;
        private MemoryInvocations(DomainToolInvocation invocation) { this.invocation = invocation; }
        @Override public Optional<DomainToolInvocation> findByIdempotencyKey(String key) {
            return invocation.idempotencyKey().equals(key) ? Optional.of(invocation) : Optional.empty();
        }
        @Override public DomainToolInvocation save(DomainToolInvocation value) { return value; }
        @Override public List<DomainToolInvocation> findByTurnId(String turnId) {
            return invocation.turnId().equals(turnId) ? List.of(invocation) : List.of();
        }
    }
}
