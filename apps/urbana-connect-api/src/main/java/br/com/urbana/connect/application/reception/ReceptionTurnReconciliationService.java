package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.reception.tools.StatefulDomainToolService;
import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocationStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionEventIds;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.TermsConsentAudit;
import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.infrastructure.hermes.HermesAgentOutputParser;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

/** Completes an uncertain turn only when Hermes exposes a changed stable cursor. */
public final class ReceptionTurnReconciliationService {
    private final HermesSessionService hermes;
    private final ReceptionConversationGateway conversations;
    private final ReceptionTranscriptGateway transcript;
    private final ReceptionTurnGateway turns;
    private final Clock clock;
    private final ActiveTurnLeaseService leases;
    private final CommercialPolicyService policy;
    private final TermsAcceptanceUseCase termsAcceptance;
    private final DomainToolInvocationGateway invocations;
    private final HermesAgentOutputParser parser = new HermesAgentOutputParser();

    public ReceptionTurnReconciliationService(HermesSessionService hermes,
                                              ReceptionConversationGateway conversations,
                                              ReceptionTranscriptGateway transcript,
                                              ReceptionTurnGateway turns,
                                              Clock clock) {
        this(hermes, conversations, transcript, turns, clock, new Dependencies(null, null, null, null));
    }

    public ReceptionTurnReconciliationService(HermesSessionService hermes,
                                              ReceptionConversationGateway conversations,
                                              ReceptionTranscriptGateway transcript,
                                              ReceptionTurnGateway turns,
                                              Clock clock,
                                              ActiveTurnLeaseService leases) {
        this(hermes, conversations, transcript, turns, clock, new Dependencies(leases, null, null, null));
    }

    public ReceptionTurnReconciliationService(HermesSessionService hermes,
                                              ReceptionConversationGateway conversations,
                                              ReceptionTranscriptGateway transcript,
                                              ReceptionTurnGateway turns,
                                              Clock clock,
                                              Dependencies dependencies) {
        this.hermes = Objects.requireNonNull(hermes, "hermes");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.turns = Objects.requireNonNull(turns, "turns");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        Dependencies configured = Objects.requireNonNull(dependencies, "dependencies");
        this.leases = configured.leases();
        this.policy = configured.policy();
        this.termsAcceptance = configured.termsAcceptance();
        this.invocations = configured.invocations();
    }

    public record Dependencies(ActiveTurnLeaseService leases,
                               CommercialPolicyService policy,
                               TermsAcceptanceUseCase termsAcceptance,
                               DomainToolInvocationGateway invocations) {
    }

    public Optional<String> reconcile(String turnOrEventId) {
        ReceptionTurn turn = turns.findById(turnOrEventId).orElseGet(() ->
                transcript.findByEventId(turnOrEventId)
                        .flatMap(message -> turns.findByInboundMessageId(message.id())).orElse(null));
        if (turn == null || turn.status() != ReceptionTurnStatus.RECONCILING) return Optional.empty();

        ReceptionConversation current = conversations.findByContactId(turn.contactId()).orElse(null);
        if (current != null && current.mode() == ReceptionMode.HUMAN) {
            String ackEventId = StatefulDomainToolService.handoffAckEventId(current);
            if (transcript.findByEventId(ackEventId).isEmpty()) {
                transcript.appendIfAbsent(new ReceptionMessage(UUID.randomUUID().toString(), ackEventId,
                        turn.correlationId(), current.id(), turn.contactId(), ReceptionMessageDirection.OUTBOUND,
                        ReceptionMessageSender.URBA, ReceptionMessageType.TEXT,
                        StatefulDomainToolService.HUMAN_HANDOFF_ACK, null, null, clock.instant()));
            }
            turns.save(turn.blockByHuman(clock.instant()));
            if (leases != null) {
                leases.releaseForReconciliation(turn.hermesSessionId(), turn.id());
            }
            return Optional.of(StatefulDomainToolService.HUMAN_HANDOFF_ACK);
        }

        Checkpoint checkpoint = Checkpoint.parse(turn.historyCheckpoint()).orElse(null);
        if (checkpoint == null) return Optional.empty();
        HermesSessionsGateway.HermesHistorySnapshot snapshot = hermes.historySnapshot(turn.hermesSessionId());
        String cursor = snapshot.stableCursor().orElse(null);
        if (cursor == null || cursor.equals(checkpoint.cursor()) || snapshot.messages().size() <= checkpoint.size()) {
            return Optional.empty();
        }

        List<HermesSessionsGateway.HermesHistoryMessage> newMessages = snapshot.messages()
                .subList(Math.min(checkpoint.size(), snapshot.messages().size()), snapshot.messages().size());
        AgentOutput output = newMessages.stream()
                .filter(message -> "assistant".equalsIgnoreCase(message.role()))
                .map(message -> parseSafely(message.content()))
                .filter(Objects::nonNull)
                .reduce((first, last) -> last)
                .orElse(null);
        if (output == null) return Optional.empty();

        ReceptionConversation conversation = conversations.findByContactId(turn.contactId()).orElse(null);
        if (conversation == null) return Optional.empty();
        if (conversation.mode() == ReceptionMode.HUMAN) {
            String ackEventId = StatefulDomainToolService.handoffAckEventId(conversation);
            if (transcript.findByEventId(ackEventId).isEmpty()) {
                transcript.appendIfAbsent(new ReceptionMessage(UUID.randomUUID().toString(), ackEventId,
                        turn.correlationId(), conversation.id(), turn.contactId(), ReceptionMessageDirection.OUTBOUND,
                        ReceptionMessageSender.URBA, ReceptionMessageType.TEXT,
                        StatefulDomainToolService.HUMAN_HANDOFF_ACK, null, null, clock.instant()));
            }
            turns.save(turn.blockByHuman(clock.instant()));
            if (leases != null) {
                leases.releaseForReconciliation(turn.hermesSessionId(), turn.id());
            }
            return Optional.of(StatefulDomainToolService.HUMAN_HANDOFF_ACK);
        }
        boolean newerInbound = transcript.findByConversationId(conversation.id()).stream()
                .filter(message -> message.direction() == ReceptionMessageDirection.INBOUND)
                .anyMatch(message -> turn.startedAt() != null && message.createdAt().isAfter(turn.startedAt())
                        && !turn.inboundMessageIds().contains(message.id()));
        if (newerInbound) {
            turns.save(turn.failSafeToRetry("STALE_INBOUND_BEFORE_RECONCILIATION", clock.instant()));
            if (leases != null) leases.releaseForReconciliation(turn.hermesSessionId(), turn.id());
            return Optional.empty();
        }
        AgentOutput safeOutput = reconcileOutput(output, conversation);
        String customerMessage = ensureInitialPresentation(conversation, safeOutput.message());
        safeOutput = new AgentOutput(customerMessage, safeOutput.nextAction(), safeOutput.handoffReason());
        if (hasNewerInbound(conversation, turn)) {
            turns.save(turn.failSafeToRetry("STALE_INBOUND_BEFORE_RECONCILIATION", clock.instant()));
            if (leases != null) leases.releaseForReconciliation(turn.hermesSessionId(), turn.id());
            return Optional.empty();
        }
        String eventId = ReceptionEventIds.outbound(turn.id(), turn.correlationId());
        ReceptionMessage outbound = new ReceptionMessage(UUID.randomUUID().toString(), eventId,
                turn.correlationId(), conversation.id(), turn.contactId(), ReceptionMessageDirection.OUTBOUND,
                ReceptionMessageSender.URBA, ReceptionMessageType.TEXT, customerMessage, null, null, clock.instant());
        transcript.appendIfAbsent(outbound);
        ReceptionMessage persistedOutbound = transcript.findByEventId(eventId).orElse(outbound);
        conversation = recordTermsPresentationIfNeeded(conversation, turn, persistedOutbound);
        // A second fence closes the common publication race where an inbound
        // is persisted while the outbound append is in flight. The transcript
        // append is idempotent; the turn is deliberately left retryable so the
        // successor turn becomes the conversational owner.
        if (hasNewerInbound(conversation, turn)) {
            turns.save(turn.failSafeToRetry("STALE_INBOUND_DURING_RECONCILIATION_PUBLICATION", clock.instant()));
            if (leases != null) leases.releaseForReconciliation(turn.hermesSessionId(), turn.id());
            return Optional.empty();
        }
        ReceptionTurn completed = turn.complete(turn.usage(), clock.instant(), safeOutput);
        turns.save(completed);
        if (leases != null) {
            leases.releaseForReconciliation(turn.hermesSessionId(), turn.id());
        }
        return Optional.of(customerMessage);
    }

    private AgentOutput reconcileOutput(AgentOutput candidate, ReceptionConversation conversation) {
        if (policy == null) return candidate;
        try {
            return policy.reconcileOutput(candidate, conversation);
        } catch (IllegalArgumentException rejected) {
            return safeOutputAfterRejection(conversation);
        }
    }

    private AgentOutput safeOutputAfterRejection(ReceptionConversation conversation) {
        return switch (conversation.paymentStatus()) {
            case PROOF_RECEIVED -> new AgentOutput(
                    "Recebi o comprovante. Agora ele aguarda validação humana; aviso assim que o pagamento for confirmado.",
                    AgentNextAction.AWAIT_PAYMENT_APPROVAL);
            case PREPARED -> new AgentOutput(
                    "O pagamento está preparado. Esta etapa da POC é uma simulação: considere 1 serviço para "
                            + "cada ambiente contratado e envie o comprovante por aqui.",
                    AgentNextAction.AWAIT_PAYMENT_PROOF);
            case NOT_STARTED, REJECTED -> new AgentOutput(
                    conversation.paymentStatus() == br.com.urbana.connect.domain.reception.model.PaymentStatus.NOT_STARTED
                            ? "Para continuar, escolha uma forma de pagamento: PIX ou cartão de crédito."
                            : "Não consigo confirmar essa etapa com segurança. Posso te orientar sobre o próximo passo?",
                    AgentNextAction.AWAIT_CUSTOMER);
            case CONFIRMED -> new AgentOutput(
                    "Não consigo confirmar essa etapa com segurança. Posso te orientar sobre o próximo passo?",
                    AgentNextAction.AWAIT_CUSTOMER);
        };
    }

    private String ensureInitialPresentation(ReceptionConversation conversation, String text) {
        boolean hasOutbound = transcript.findByConversationId(conversation.id()).stream()
                .anyMatch(message -> message.direction() == ReceptionMessageDirection.OUTBOUND);
        if (hasOutbound || identifiesUrbaAndUrbana(text)) return text;
        return "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. " + text;
    }

    private static boolean identifiesUrbaAndUrbana(String text) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("urba") && normalized.contains("urbana do brasil");
    }

    private boolean hasNewerInbound(ReceptionConversation conversation, ReceptionTurn turn) {
        return transcript.findByConversationId(conversation.id()).stream()
                .filter(message -> message.direction() == ReceptionMessageDirection.INBOUND)
                .anyMatch(message -> turn.startedAt() != null && message.createdAt().isAfter(turn.startedAt())
                        && !turn.inboundMessageIds().contains(message.id()));
    }

    private ReceptionConversation recordTermsPresentationIfNeeded(ReceptionConversation conversation,
                                                                   ReceptionTurn turn,
                                                                   ReceptionMessage outbound) {
        if (termsAcceptance == null || invocations == null || conversation.termsStatus() != TermsStatus.PRESENTED
                || conversation.activeTermsConsentId() != null || conversation.contractingUnitId() == null
                || conversation.environmentLabel() == null || conversation.environmentSourceMessageId() == null) {
            return conversation;
        }
        Optional<DomainToolInvocation> invocation = invocations.findByTurnId(turn.id()).stream()
                .filter(value -> value.toolName() == DomainToolName.PREPARE_TERMS)
                .filter(value -> value.status() == DomainToolInvocationStatus.SUCCEEDED)
                .reduce((first, last) -> last);
        if (invocation.isEmpty()) return conversation;
        Object payload = invocation.get().resultPayload();
        String resource = payload instanceof Map<?, ?> map && map.get("url") != null
                ? map.get("url").toString() : null;
        if (resource == null || resource.isBlank() || outbound.text() == null
                || !outbound.text().contains(resource)) return conversation;
        String presentationId = "terms:" + conversation.id() + ":" + conversation.contractingUnitId()
                + ":" + invocation.get().id();
        TermsConsentAudit audit = new TermsConsentAudit(presentationId, conversation.id(), conversation.contactId(),
                turn.id(), conversation.contractingUnitId(), conversation.environmentLabel(),
                conversation.environmentSourceMessageId(), conversation.selectedService(), resource, null,
                invocation.get().id(), outbound.id(), outbound.createdAt(), null, null, null, null, outbound.createdAt(),
                TermsConsentStatus.PRESENTED, conversation.version(), null);
        termsAcceptance.recordPresentation(audit);
        return conversations.save(conversation.activateTermsConsent(presentationId, outbound.createdAt()));
    }

    private AgentOutput parseSafely(String content) {
        try {
            return parser.parse(content);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private record Checkpoint(String cursor, int size) {
        private static Optional<Checkpoint> parse(String value) {
            if (value == null || value.isBlank()) return Optional.empty();
            int separator = value.lastIndexOf('|');
            if (separator <= 0 || separator == value.length() - 1) return Optional.empty();
            try {
                int size = Integer.parseInt(value.substring(separator + 1));
                return size < 0 ? Optional.empty() : Optional.of(new Checkpoint(value.substring(0, separator), size));
            } catch (NumberFormatException invalid) {
                return Optional.empty();
            }
        }
    }
}
