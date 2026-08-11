package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionEventIds;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;
import br.com.urbana.connect.infrastructure.hermes.HermesAgentOutputParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Canonical ingress orchestration. Hermes owns the dialogue and invokes the
 * allowlisted tools; this class only persists, leases, parses and reconciles
 * the resulting turn against Urbana's deterministic state.
 */
public final class ReceptionOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReceptionOrchestrator.class);
    private static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";
    private static final Duration DEFAULT_DELAY_THRESHOLD = Duration.ofSeconds(5);
    private final HermesSessionService hermes;
    private final ReceptionConversationGateway conversations;
    private final CustomerFactGateway facts;
    private final ReceptionTranscriptGateway transcript;
    private final ReceptionTurnGateway turns;
    private final CommercialPolicyService policy;
    private final ReceptionTurnCoordinator coordinator;
    private final ActiveTurnLeaseService leases;
    private final DomainToolInvocationGateway invocations;
    private final HermesAgentOutputParser parser;
    private final ReturningCustomerService returningCustomers;
    private final Clock clock;
    private final ReceptionMetrics metrics;
    private final NonProspectPolicy nonProspectPolicy;
    private final Duration delayThreshold;
    private final Map<String, NonProspectPolicy.State> nonProspectStates = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> hermesChatCallsByContact = new ConcurrentHashMap<>();

    public ReceptionOrchestrator(HermesSessionService hermes,
                                 ReceptionConversationGateway conversations,
                                 CustomerFactGateway facts,
                                 ReceptionTranscriptGateway transcript,
                                 ReceptionTurnGateway turns,
                                 CommercialPolicyService policy,
                                 ReceptionTurnCoordinator coordinator,
                                 ActiveTurnLeaseService leases,
                                 DomainToolInvocationGateway invocations,
                                 Clock clock) {
        this(hermes, conversations, facts, transcript, turns, policy, coordinator, leases,
                invocations, clock, new ReceptionMetrics(), null);
    }

    public ReceptionOrchestrator(HermesSessionService hermes,
                                 ReceptionConversationGateway conversations,
                                 CustomerFactGateway facts,
                                 ReceptionTranscriptGateway transcript,
                                 ReceptionTurnGateway turns,
                                 CommercialPolicyService policy,
                                 ReceptionTurnCoordinator coordinator,
                                 ActiveTurnLeaseService leases,
                                 DomainToolInvocationGateway invocations,
                                 Clock clock,
                                 ReceptionMetrics metrics) {
        this(hermes, conversations, facts, transcript, turns, policy, coordinator, leases,
                invocations, clock, metrics, null);
    }

    public ReceptionOrchestrator(HermesSessionService hermes,
                                 ReceptionConversationGateway conversations,
                                 CustomerFactGateway facts,
                                 ReceptionTranscriptGateway transcript,
                                 ReceptionTurnGateway turns,
                                 CommercialPolicyService policy,
                                 ReceptionTurnCoordinator coordinator,
                                 ActiveTurnLeaseService leases,
                                 DomainToolInvocationGateway invocations,
                                 Clock clock,
                                 ReceptionMetrics metrics,
                                 ReturningCustomerService returningCustomers) {
        this(hermes, conversations, facts, transcript, turns, policy, coordinator, leases,
                invocations, clock, metrics, returningCustomers, new NonProspectPolicy());
    }

    public ReceptionOrchestrator(HermesSessionService hermes,
                                 ReceptionConversationGateway conversations,
                                 CustomerFactGateway facts,
                                 ReceptionTranscriptGateway transcript,
                                 ReceptionTurnGateway turns,
                                 CommercialPolicyService policy,
                                 ReceptionTurnCoordinator coordinator,
                                 ActiveTurnLeaseService leases,
                                 DomainToolInvocationGateway invocations,
                                 Clock clock,
                                 ReceptionMetrics metrics,
                                 ReturningCustomerService returningCustomers,
                                 NonProspectPolicy nonProspectPolicy) {
        this(hermes, conversations, facts, transcript, turns, policy, coordinator, leases,
                invocations, clock, metrics, returningCustomers, nonProspectPolicy,
                DEFAULT_DELAY_THRESHOLD);
    }

    public ReceptionOrchestrator(HermesSessionService hermes,
                                 ReceptionConversationGateway conversations,
                                 CustomerFactGateway facts,
                                 ReceptionTranscriptGateway transcript,
                                 ReceptionTurnGateway turns,
                                 CommercialPolicyService policy,
                                 ReceptionTurnCoordinator coordinator,
                                 ActiveTurnLeaseService leases,
                                 DomainToolInvocationGateway invocations,
                                 Clock clock,
                                 ReceptionMetrics metrics,
                                 ReturningCustomerService returningCustomers,
                                 NonProspectPolicy nonProspectPolicy,
                                 Duration delayThreshold) {
        this.hermes = Objects.requireNonNull(hermes, "hermes");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.turns = Objects.requireNonNull(turns, "turns");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.leases = leases;
        this.invocations = invocations;
        this.parser = new HermesAgentOutputParser();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.metrics = metrics == null ? new ReceptionMetrics() : metrics;
        this.returningCustomers = returningCustomers == null
                ? new ReturningCustomerService(facts, policy, this.clock) : returningCustomers;
        this.nonProspectPolicy = Objects.requireNonNull(nonProspectPolicy, "nonProspectPolicy");
        if (delayThreshold == null || delayThreshold.isZero() || delayThreshold.isNegative()) {
            throw new IllegalArgumentException("delay threshold must be positive");
        }
        this.delayThreshold = delayThreshold;
    }

    public ReceptionOrchestrator(HermesSessionService hermes,
                                 ReceptionConversationGateway conversations,
                                 CustomerFactGateway facts,
                                 ReceptionTranscriptGateway transcript,
                                 ReceptionTurnGateway turns,
                                 CommercialPolicyService policy,
                                 ReceptionTurnCoordinator coordinator,
                                 ActiveTurnLeaseService leases,
                                 Clock clock) {
        this(hermes, conversations, facts, transcript, turns, policy, coordinator,
                leases, null, clock);
    }

    public ReceptionOrchestrator(HermesSessionService hermes,
                                 ReceptionConversationGateway conversations,
                                 CustomerFactGateway facts,
                                 ReceptionTranscriptGateway transcript,
                                 ReceptionTurnGateway turns,
                                 CommercialPolicyService policy,
                                 ReceptionTurnCoordinator coordinator,
                                 Clock clock) {
        this(hermes, conversations, facts, transcript, turns, policy, coordinator,
                null, null, clock);
    }

    public ReceptionOrchestrator(HermesSessionService hermes,
                                 ReceptionConversationGateway conversations,
                                 CustomerFactGateway facts,
                                 ReceptionTranscriptGateway transcript,
                                 ReceptionTurnGateway turns,
                                 CommercialPolicyService policy,
                                 ReceptionTurnCoordinator coordinator) {
        this(hermes, conversations, facts, transcript, turns, policy, coordinator,
                null, null, Clock.systemUTC());
    }

    public TurnReceipt process(InboundConversationEvent event) {
        Objects.requireNonNull(event, "event");
        return processBatch(List.of(event));
    }

    /**
     * Checks the durable transcript before a synthetic event enters the
     * batching queue. Retryable or incomplete persisted turns remain eligible
     * for recovery; only finalized turns are returned as duplicates.
     */
    public Optional<TurnReceipt> duplicateReceiptIfFinalized(InboundConversationEvent event) {
        Objects.requireNonNull(event, "event");
        return coordinator.serialize(event.contactId(), () -> {
            Optional<ReceptionMessage> existing = transcript.findByEventId(event.eventId());
            if (existing.isEmpty()) {
                return Optional.empty();
            }
            ReceptionMessage inbound = existing.orElseThrow();
            if (!inbound.contactId().equals(event.contactId())) {
                throw new IllegalArgumentException("eventId is already bound to another contact");
            }
            Optional<ReceptionTurn> prior = turns.findByInboundMessageId(inbound.id());
            if (prior.isEmpty() || prior.get().isActiveOrUncertain() || isRetryableTurn(prior.get())) {
                return Optional.empty();
            }
            return Optional.of(duplicateReceipt(event, inbound));
        });
    }

    /** Processes one released POC batch as a single conversational turn. */
    public TurnReceipt processBatch(List<InboundConversationEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("at least one inbound event is required");
        }
        List<InboundConversationEvent> submitted = List.copyOf(events);
        String contactId = submitted.getFirst().contactId();
        if (submitted.stream().anyMatch(event -> !contactId.equals(event.contactId()))) {
            throw new IllegalArgumentException("a batch cannot contain multiple contacts");
        }
        List<InboundConversationEvent> batch = deduplicateEvents(submitted);
        return coordinator.serialize(contactId, () -> processBatchUnderLock(batch));
    }

    private TurnReceipt processBatchUnderLock(List<InboundConversationEvent> events) {
        List<InboundConversationEvent> newEvents = new ArrayList<>();
        ReceptionMessage existingMessage = null;
        for (InboundConversationEvent event : events) {
            Optional<ReceptionMessage> duplicate = transcript.findByEventId(event.eventId());
            if (duplicate.isEmpty()) {
                newEvents.add(event);
                continue;
            }
            if (!duplicate.get().contactId().equals(event.contactId())) {
                throw new IllegalArgumentException("eventId is already bound to another contact");
            }
            existingMessage = duplicate.get();
            if (events.size() == 1) {
                Optional<ReceptionTurn> prior = turns.findByInboundMessageId(existingMessage.id());
                if (prior.filter(this::isRetryableTurn).isPresent()) {
                    return retry(event, existingMessage, prior.orElseThrow());
                }
                if (prior.filter(ReceptionTurn::isActiveOrUncertain).isPresent()) {
                    return inProgressReceipt(event, prior.orElseThrow());
                }
                if (prior.isEmpty()) {
                    return recoverPersistedInbound(event, existingMessage);
                }
                return duplicateReceipt(event, existingMessage);
            }
        }
        if (newEvents.isEmpty()) {
            return duplicateReceipt(events.getLast(), Objects.requireNonNull(existingMessage, "existingMessage"));
        }
        return processNew(newEvents);
    }

    /** Backend/operator-only checkpoint; no Hermes tool or model input can call it. */
    public TurnReceipt approvePaymentProof(String contactId) {
        require(contactId, "contactId");
        return coordinator.serialize(contactId, () -> {
            ReceptionConversation conversation = conversations.findByContactId(contactId)
                    .orElseThrow(() -> new IllegalStateException("conversation does not exist"));
            ReceptionConversation approved = policy.approvePaymentProof(conversation, clock.instant());
            conversations.save(approved);
            String eventId = "approval:" + contactId + ":" + approved.version();
            String correlationId = UUID.randomUUID().toString();
            AgentOutput output = new AgentOutput(policy.briefingFor(approved), AgentNextAction.NONE);
            appendOutbound(approved, eventId, correlationId, output.message());
            return new TurnReceipt(eventId, correlationId, TurnStatus.COMPLETED, output, null);
        });
    }

    public Map<String, Object> projection(String contactId) {
        ReceptionConversation conversation = conversations.findByContactId(contactId).orElse(null);
        List<CustomerFact> customerFacts = conversation == null ? List.of() : facts.findByContactId(contactId);
        List<ReceptionMessage> messages = conversation == null ? List.of()
                : transcript.findByConversationId(conversation.id());
        List<br.com.urbana.connect.domain.reception.model.DomainToolInvocation> toolLedger = invocations == null
                ? List.of() : invocations.findByContactId(contactId);
        List<Map<String, Object>> safeToolLedger = toolLedger.stream().map(invocation -> {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("toolName", invocation.toolName().wireName());
            evidence.put("status", invocation.status().name());
            evidence.put("resultCode", invocation.resultCode());
            return evidence;
        }).toList();
        NonProspectPolicy.State probeState = nonProspectStates.getOrDefault(
                contactId, NonProspectPolicy.State.initial());
        boolean commercialOpportunityCreated = conversation != null
                && (conversation.selectedService() != null
                || conversation.termsStatus() != br.com.urbana.connect.domain.reception.model.TermsStatus.NOT_PRESENTED
                || conversation.paymentStatus() != PaymentStatus.NOT_STARTED);
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("contactId", contactId);
        projection.put("conversation", conversation == null ? Map.of() : conversation);
        projection.put("facts", customerFacts);
        projection.put("messages", messages);
        projection.put("turn", turns.findLatestByContactId(contactId)
                .map(ReceptionOrchestrator::safeTurnProjection).orElse(null));
        projection.put("toolInvocations", safeToolLedger);
        projection.put("hermesChatCalls", hermesChatCallsByContact.getOrDefault(contactId, new LongAdder()).sum());
        projection.put("lightProbesUsed", probeState.lightProbesUsed());
        projection.put("commercialOpportunityCreated", commercialOpportunityCreated);
        return projection;
    }

    private static Map<String, Object> safeTurnProjection(ReceptionTurn turn) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", turn.status().name());
        summary.put("correlationId", turn.correlationId());
        summary.put("attempt", turn.attempt());
        summary.put("retryAllowed", turn.retryAllowed());
        summary.put("failureClass", turn.failureClass());
        summary.put("acceptedAt", turn.acceptedAt());
        summary.put("startedAt", turn.startedAt());
        summary.put("finishedAt", turn.finishedAt());
        return summary;
    }

    private TurnReceipt processNew(List<InboundConversationEvent> events) {
        InboundConversationEvent first = events.getFirst();
        InboundConversationEvent last = events.getLast();
        Instant now = first.occurredAt();
        ReceptionConversation conversation = conversations.findByContactId(first.contactId())
                .orElseGet(() -> conversations.save(ReceptionConversation.start(first.contactId(), now)));
        String correlationId = UUID.randomUUID().toString();
        List<ReceptionMessage> inboundMessages = new ArrayList<>();
        for (InboundConversationEvent event : events) {
            ReceptionMessage inbound = new ReceptionMessage(UUID.randomUUID().toString(), event.eventId(),
                    correlationId, conversation.id(), event.contactId(), ReceptionMessageDirection.INBOUND,
                    ReceptionMessageSender.CONTACT, event.type(),
                    event.conversationalText().isBlank() ? null : event.conversationalText(),
                    event.mediaFixture(), event.providerMessageId(), event.occurredAt());
            if (transcript.appendIfAbsent(inbound)) {
                inboundMessages.add(inbound);
            }
        }
        if (inboundMessages.isEmpty()) {
            ReceptionMessage duplicate = transcript.findByEventId(last.eventId())
                    .orElseThrow(() -> new IllegalStateException("inbound event disappeared during append"));
            return duplicateReceipt(last, duplicate);
        }
        return processPersisted(events, conversation, inboundMessages, correlationId, now);
    }

    private TurnReceipt recoverPersistedInbound(InboundConversationEvent event, ReceptionMessage inbound) {
        ReceptionConversation conversation = conversations.findByContactId(event.contactId())
                .orElseGet(() -> conversations.save(ReceptionConversation.start(event.contactId(), event.occurredAt())));
        return processPersisted(List.of(event), conversation, List.of(inbound), inbound.correlationId(),
                inbound.createdAt());
    }

    private TurnReceipt processPersisted(List<InboundConversationEvent> events,
                                         ReceptionConversation conversation,
                                         List<ReceptionMessage> inboundMessages,
                                         String correlationId,
                                         Instant now) {
        InboundConversationEvent first = events.getFirst();
        InboundConversationEvent last = events.getLast();
        Optional<ReceptionTurn> activePrior = turns.findLatestByContactId(first.contactId())
                .filter(ReceptionTurn::isActiveOrUncertain)
                .filter(prior -> inboundMessages.stream().noneMatch(message ->
                        prior.inboundMessageIds().contains(message.id())));
        if (activePrior.isPresent()) {
            return inProgressReceipt(last, activePrior.orElseThrow());
        }
        // Human handoff is an exclusive mode. Persist this inbound message,
        // record a blocked turn for audit, and return without resolving a
        // Hermes session, acquiring a lease, invoking tools, or publishing an
        // automated outbound message.
        if (conversation.mode() == ReceptionMode.HUMAN) {
            ReceptionTurn blockedTurn = new ReceptionTurn(UUID.randomUUID().toString(), correlationId,
                    first.contactId(), "human:" + conversation.id(), inboundMessages.stream()
                            .map(ReceptionMessage::id).toList(),
                    ReceptionTurnStatus.QUEUED, null, null, AgentUsage.empty(), null)
                    .blockByHuman(now);
            turns.save(blockedTurn);
            metrics.recordTurn(blockedTurn);
            return new TurnReceipt(last.eventId(), correlationId, TurnStatus.BLOCKED_BY_HUMAN, null, null);
        }

        return startTurn(events, conversation, inboundMessages, correlationId, now);
    }

    private static List<InboundConversationEvent> deduplicateEvents(List<InboundConversationEvent> events) {
        Map<String, InboundConversationEvent> byEventId = new LinkedHashMap<>();
        events.forEach(event -> byEventId.putIfAbsent(event.eventId(), event));
        return List.copyOf(byEventId.values());
    }

    private TurnReceipt retry(InboundConversationEvent event, ReceptionMessage inbound,
                              ReceptionTurn failedTurn) {
        ReceptionConversation conversation = conversations.findByContactId(event.contactId())
                .orElseThrow(() -> new IllegalStateException("conversation does not exist for retry"));
        ReceptionTurn retryTurn;
        String sessionId = failedTurn.hermesSessionId();
        try {
            sessionId = hermes.resolve(event.contactId()).sessionId();
            retryTurn = ReceptionTurn.queued(failedTurn.id(), failedTurn.correlationId(), failedTurn.contactId(),
                    sessionId, failedTurn.inboundMessageIds(), event.occurredAt(),
                    captureHistoryCheckpoint(sessionId)).start(event.occurredAt());
        } catch (RuntimeException exception) {
            retryTurn = failedTurn.start(event.occurredAt());
            turns.save(retryTurn);
            return failedTurn(List.of(event), conversation, retryTurn, exception);
        }
        turns.save(retryTurn);
        ReceptionTurn activeRetryTurn = retryTurn;
        try {
            if (leases == null) {
                return runTurn(List.of(event), conversation, activeRetryTurn);
            }
            return leases.withLease(sessionId, activeRetryTurn.id(), event.contactId(), event.eventId(),
                    () -> runTurn(List.of(event), conversation, activeRetryTurn));
        } catch (RuntimeException exception) {
            recordLeaseBlockIfApplicable(exception, activeRetryTurn);
            return failedTurn(List.of(event), conversation, retryTurn, exception);
        }
    }

    private TurnReceipt startTurn(List<InboundConversationEvent> events,
                                  ReceptionConversation conversation,
                                  List<ReceptionMessage> inboundMessages,
                                  String correlationId,
                                  Instant startedAt) {
        InboundConversationEvent first = events.getFirst();
        InboundConversationEvent last = events.getLast();
        ReceptionTurn turn;
        HermesSessionService.SessionResolution resolution;
        try {
            resolution = hermes.resolve(first.contactId());
            turn = ReceptionTurn.queued(UUID.randomUUID().toString(), correlationId, first.contactId(),
                    resolution.sessionId(), inboundMessages.stream().map(ReceptionMessage::id).toList(),
                    startedAt, captureHistoryCheckpoint(resolution.sessionId())).start(startedAt);
        } catch (RuntimeException exception) {
            turn = ReceptionTurn.queued(UUID.randomUUID().toString(), correlationId, first.contactId(),
                    "pending:" + first.contactId(), inboundMessages.stream().map(ReceptionMessage::id).toList(),
                    startedAt, null).start(startedAt);
            turns.save(turn);
            return failedTurn(events, conversation, turn, exception);
        }
        turns.save(turn);
        ReceptionTurn activeTurn = turn;
        try {
            if (leases == null) {
                return runTurn(events, conversation, activeTurn);
            }
            return leases.withLease(resolution.sessionId(), activeTurn.id(), first.contactId(), last.eventId(),
                    () -> runTurn(events, conversation, activeTurn));
        } catch (RuntimeException exception) {
            recordLeaseBlockIfApplicable(exception, activeTurn);
            return failedTurn(events, conversation, turn, exception);
        }
    }

    private TurnReceipt processNonProspect(List<InboundConversationEvent> events,
                                           ReceptionConversation conversation,
                                           List<ReceptionMessage> inboundMessages,
                                           String correlationId,
                                           String policyInput) {
        InboundConversationEvent last = events.getLast();
        NonProspectPolicy.State state = nonProspectStates.getOrDefault(
                last.contactId(), NonProspectPolicy.State.initial());
        NonProspectPolicy.Decision decision = nonProspectPolicy.decide(policyInput, state);
        nonProspectStates.put(last.contactId(), decision.nextState());
        ReceptionConversation canonical = conversation;
        if (decision.disposition() == NonProspectPolicy.Disposition.OFFER_HUMAN) {
            canonical = conversations.save(conversation.requestHumanHandoff(
                    decision.output().handoffReason() == null
                            ? "pedido não comercial não confirmado" : decision.output().handoffReason(),
                    last.occurredAt()));
        }
        ReceptionTurn turn = new ReceptionTurn(UUID.randomUUID().toString(), correlationId, last.contactId(),
                "policy:" + canonical.id(), inboundMessages.stream().map(ReceptionMessage::id).toList(),
                ReceptionTurnStatus.QUEUED, null, null, AgentUsage.empty(), null).start(last.occurredAt());
        turns.save(turn);
        appendOutbound(canonical, last.eventId() + ":outbound", correlationId, decision.output().message());
        ReceptionTurn completed = turn.complete(AgentUsage.empty(), clock.instant(), decision.output());
        turns.save(completed);
        metrics.recordTurn(completed);
        return new TurnReceipt(last.eventId(), correlationId, TurnStatus.COMPLETED, decision.output(), null);
    }

    private TurnReceipt runTurn(List<InboundConversationEvent> events, ReceptionConversation initial,
                                ReceptionTurn turn) {
        InboundConversationEvent first = events.getFirst();
        InboundConversationEvent last = events.getLast();
        ReceptionConversation beforeChat = initial;
        Optional<InboundConversationEvent> interactiveSelection = events.stream()
                .filter(event -> event.type() == ReceptionMessageType.INTERACTIVE
                        && event.interactiveReplyId() != null)
                .findFirst();
        if (interactiveSelection.isPresent()) {
            InboundConversationEvent selection = interactiveSelection.orElseThrow();
            String service = policy.serviceTypeForInteractiveReply(selection.interactiveReplyId());
            beforeChat = conversations.save(policy.selectService(
                    beforeChat, service, last.occurredAt()));
            persistInteractiveServiceFact(first.contactId(), service, selection);
        }
        Optional<InboundConversationEvent> acceptance = events.stream()
                .filter(event -> explicitTermsAcceptance(event.conversationalText())).findFirst();
        if (initial.termsStatus() == br.com.urbana.connect.domain.reception.model.TermsStatus.PRESENTED
                && acceptance.isPresent()) {
            beforeChat = conversations.save(policy.acceptTerms(initial, acceptance.get().occurredAt()));
        }
        Optional<InboundConversationEvent> proof = events.stream()
                .filter(InboundConversationEvent::isPaymentProof).findFirst();
        if (proof.isPresent() && initial.paymentStatus() == PaymentStatus.PREPARED) {
            ReceptionConversation proofBase = beforeChat.paymentStatus() == PaymentStatus.PREPARED
                    ? beforeChat : conversations.findByContactId(first.contactId()).orElse(beforeChat);
            beforeChat = conversations.save(policy.receivePaymentProof(proofBase, proof.get().occurredAt()));
        }
        String input = events.stream().map(InboundConversationEvent::conversationalText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right).orElse("");
        List<String> images = events.stream()
                .filter(event -> event.mediaFixture() != null
                        && (event.type() == ReceptionMessageType.IMAGE
                        || event.type() == ReceptionMessageType.PAYMENT_PROOF
                        || event.isPaymentProof()))
                .map(InboundConversationEvent::mediaFixture).toList();
        hermesChatCallsByContact.computeIfAbsent(first.contactId(), ignored -> new LongAdder()).increment();
        HermesSessionsGateway.HermesChatResult chat = chatWithDelayTracking(first.contactId(),
                new HermesSessionsGateway.HermesChatRequest(input, images, null, null, null), turn);
        ReceptionConversation canonical = conversations.findByContactId(first.contactId()).orElse(beforeChat);
        List<CustomerFact> currentFacts = facts.findByContactId(first.contactId());
        if (canonical.selectedService() != null
                && canonical.termsStatus() == br.com.urbana.connect.domain.reception.model.TermsStatus.NOT_PRESENTED
                && policy.isIcpComplete(currentFacts, last.occurredAt())) {
            canonical = conversations.save(policy.presentTerms(canonical, currentFacts, last.occurredAt()));
        }
        if (canonical.termsStatus() == br.com.urbana.connect.domain.reception.model.TermsStatus.PRESENTED
                && acceptance.isPresent()) {
            canonical = conversations.save(policy.acceptTerms(canonical, acceptance.get().occurredAt()));
        }
        List<br.com.urbana.connect.domain.reception.model.DomainToolInvocation> ledger = invocations == null
                ? List.of() : invocations.findByTurnId(turn.id());
        // The handoff tool may have changed the authoritative conversation
        // while Hermes was generating this turn. Human mode is terminal for
        // automated publication, so do not pass the agent output through the
        // commercial policy, which intentionally rejects human-mode checks.
        if (canonical.mode() == ReceptionMode.HUMAN) {
            ReceptionTurn blocked = turn.blockByHuman(clock.instant());
            turns.save(blocked);
            metrics.recordTurn(blocked, ledger);
            return new TurnReceipt(last.eventId(), turn.correlationId(), TurnStatus.BLOCKED_BY_HUMAN, null, null);
        }
        // The normal conversation path is a transparent pass-through. The
        // parser only unwraps a compatible legacy message envelope; it never
        // validates, rewrites or semantically reconciles the textual response.
        AgentOutput output = parser.parse(chat.content());
        ReceptionTurn currentTurn = turns.findById(turn.id()).orElse(null);
        if (currentTurn != null && (currentTurn.isTerminal()
                || currentTurn.status() == ReceptionTurnStatus.RECONCILING)) {
            return new TurnReceipt(last.eventId(), currentTurn.correlationId(),
                    turnStatus(currentTurn.status()), currentTurn.output(), currentTurn.failureClass());
        }
        appendOutbound(canonical, last.eventId() + ":outbound", turn.correlationId(), output.message());
        ReceptionTurn completed = turn.complete(chat.usage(), clock.instant(), output);
        turns.save(completed);
        metrics.recordTurn(completed, ledger);
        TurnStatus status = canonical.mode() == ReceptionMode.HUMAN
                ? TurnStatus.BLOCKED_BY_HUMAN : TurnStatus.COMPLETED;
        return new TurnReceipt(last.eventId(), turn.correlationId(), status, output, null);
    }

    private HermesSessionsGateway.HermesChatResult chatWithDelayTracking(
            String contactId, HermesSessionsGateway.HermesChatRequest request, ReceptionTurn turn) {
        ScheduledExecutorService delayExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "urbana-reception-delay-observer");
            thread.setDaemon(true);
            return thread;
        });
        AtomicBoolean chatFinished = new AtomicBoolean(false);
        AtomicBoolean delayedRecorded = new AtomicBoolean(false);
        long thresholdNanos = delayThreshold.toNanos();
        if (thresholdNanos <= 0) {
            thresholdNanos = 1;
        }
        long startedNanos = System.nanoTime();
        ScheduledFuture<?> delayMarker = delayExecutor.schedule(
                () -> {
                    if (!chatFinished.get()) {
                        markDelayedIfStillActive(turn.id(), delayedRecorded);
                    }
                },
                thresholdNanos, TimeUnit.NANOSECONDS);
        try {
            return hermes.chat(contactId, request);
        } finally {
            chatFinished.set(true);
            if (!delayedRecorded.get()
                    && System.nanoTime() - startedNanos >= thresholdNanos) {
                markDelayedIfStillActive(turn.id(), delayedRecorded);
            }
            delayMarker.cancel(false);
            delayExecutor.shutdownNow();
            try {
                if (!delayExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    delayExecutor.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                delayExecutor.shutdownNow();
            }
        }
    }

    private void markDelayedIfStillActive(String turnId, AtomicBoolean delayedRecorded) {
        if (!delayedRecorded.compareAndSet(false, true)) {
            return;
        }
        try {
            ReceptionTurn current = turns.findById(turnId).orElse(null);
            if (current == null || (current.status() != ReceptionTurnStatus.QUEUED
                    && current.status() != ReceptionTurnStatus.RUNNING)) {
                return;
            }
            ReceptionTurn delayed = current.delay(clock.instant());
            if (delayed.status() != ReceptionTurnStatus.DELAYED) {
                return;
            }
            ReceptionTurn persisted = turns.save(delayed);
            if (persisted != null && persisted.status() == ReceptionTurnStatus.DELAYED) {
                metrics.recordTurn(persisted);
            }
        } catch (RuntimeException ignored) {
            // A lifecycle observation must never replace the Hermes result or
            // turn failure. The worker/reconciler remains authoritative.
        }
    }

    private void persistInteractiveServiceFact(String contactId, String serviceType,
                                               InboundConversationEvent selection) {
        List<CustomerFact> current = facts.findCurrentByContactId(contactId, selection.occurredAt());
        if (current.stream().anyMatch(fact -> "SELECTED_SERVICE".equals(fact.type())
                && serviceType.equals(fact.value())
                && selection.eventId().equals(fact.sourceMessageId())
                && fact.isConfirmedCurrentAt(selection.occurredAt()))) {
            return;
        }
        current.stream()
                .filter(fact -> "SELECTED_SERVICE".equals(fact.type())
                        && fact.isConfirmedCurrentAt(selection.occurredAt()))
                .forEach(previous -> facts.save(previous.supersede(UUID.randomUUID().toString(),
                        selection.occurredAt())));
        facts.save(CustomerFact.confirmed(contactId, "SELECTED_SERVICE", serviceType,
                selection.eventId(), selection.occurredAt()));
    }

    private TurnReceipt failedTurn(List<InboundConversationEvent> events,
                                   ReceptionConversation conversation,
                                   ReceptionTurn turn,
                                   RuntimeException exception) {
        InboundConversationEvent last = events.getLast();
        List<br.com.urbana.connect.domain.reception.model.DomainToolInvocation> ledger = toolLedger(turn);
        StackTraceElement source = exception.getStackTrace().length == 0
                ? null : exception.getStackTrace()[0];
        String sourceLocation = source == null ? "unknown"
                : source.getClassName() + "." + source.getMethodName() + ":" + source.getLineNumber();
        LOGGER.warn("reception_turn_failed correlationId={} turnId={} exceptionType={} source={}",
                turn.correlationId(), turn.id(), exception.getClass().getSimpleName(), sourceLocation);
        if (exception instanceof br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesSessionsException hermesFailure) {
            String failureClass = hermesFailureClass(hermesFailure);
            ReceptionTurn classified;
            TurnStatus status;
            if (hermesFailure.phase()
                    == br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesFailurePhase.POST_DISPATCH_AMBIGUOUS) {
                classified = turn.reconcile(failureClass, clock.instant());
                status = TurnStatus.RECONCILING;
            } else if (hermesFailure.retryAllowed()) {
                classified = turn.failSafeToRetry(failureClass, clock.instant());
                status = TurnStatus.FAILED_SAFE_TO_RETRY;
            } else {
                classified = turn.failTerminal(failureClass, clock.instant());
                status = TurnStatus.FAILED_TERMINAL;
            }
            turns.save(classified);
            metrics.recordTurn(classified, ledger);
            return new TurnReceipt(last.eventId(), turn.correlationId(), status, null,
                    failureClass);
        }
        if (isRetryableHermesFailure(exception)) {
            String failureClass = "HERMES_RETRYABLE_TRANSPORT";
            ReceptionTurn failed = turn.failSafeToRetry(failureClass, clock.instant());
            turns.save(failed);
            metrics.recordTurn(failed, ledger);
            return new TurnReceipt(last.eventId(), turn.correlationId(), TurnStatus.FAILED_SAFE_TO_RETRY,
                    null, failureClass);
        }
        String failureClass = "APPLICATION_FAILURE";
        ReceptionTurn failed = turn.failTerminal(failureClass, clock.instant());
        turns.save(failed);
        metrics.recordTurn(failed, ledger);
        return new TurnReceipt(last.eventId(), turn.correlationId(), TurnStatus.FAILED_TERMINAL,
                null, failureClass);
    }

    private List<br.com.urbana.connect.domain.reception.model.DomainToolInvocation> toolLedger(
            ReceptionTurn turn) {
        return invocations == null ? List.of() : invocations.findByTurnId(turn.id());
    }

    private void recordLeaseBlockIfApplicable(RuntimeException exception, ReceptionTurn turn) {
        if (exception instanceof ActiveTurnLeaseService.LeaseUnavailableException) {
            metrics.recordConcurrentTurnBlock(turn.correlationId(), turn.id(), turn.attempt());
        }
    }

    private boolean isRetryableTurn(ReceptionTurn turn) {
        return turn.retryAllowed()
                && (turn.status() == ReceptionTurnStatus.FAILED
                || turn.status() == ReceptionTurnStatus.FAILED_SAFE_TO_RETRY)
                && (turn.failureCode() == null || FAILED_RETRYABLE.equals(turn.failureCode())
                || turn.status() == ReceptionTurnStatus.FAILED_SAFE_TO_RETRY);
    }

    private String captureHistoryCheckpoint(String sessionId) {
        try {
            HermesSessionsGateway.HermesHistorySnapshot snapshot = hermes.historySnapshot(sessionId);
            return snapshot.stableCursor().map(cursor -> cursor + "|" + snapshot.messages().size()).orElse(null);
        } catch (RuntimeException ignored) {
            // A missing checkpoint is fail-closed during ambiguity; it must
            // never prevent the initial dispatch from being accepted.
            return null;
        }
    }

    private static String hermesFailureClass(
            br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesSessionsException failure) {
        if (failure.phase()
                == br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesFailurePhase.POST_DISPATCH_AMBIGUOUS) {
            return failure.status() == 0 ? "HERMES_TIMEOUT_AFTER_DISPATCH" : "HERMES_AMBIGUOUS_AFTER_DISPATCH";
        }
        if (failure.phase()
                == br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesFailurePhase.REMOTE_TERMINAL) {
            return "HERMES_REMOTE_TERMINAL";
        }
        return "HERMES_REJECTED_BEFORE_DISPATCH";
    }

    private static TurnReceipt inProgressReceipt(InboundConversationEvent event, ReceptionTurn prior) {
        return new TurnReceipt(event.eventId(), prior.correlationId(), turnStatus(prior.status()), null,
                prior.failureClass());
    }

    private static TurnStatus turnStatus(ReceptionTurnStatus status) {
        return switch (status) {
            case QUEUED -> TurnStatus.QUEUED;
            case RUNNING -> TurnStatus.RUNNING;
            case DELAYED -> TurnStatus.DELAYED;
            case RECONCILING -> TurnStatus.RECONCILING;
            case COMPLETED -> TurnStatus.COMPLETED;
            case FAILED_SAFE_TO_RETRY -> TurnStatus.FAILED_SAFE_TO_RETRY;
            case FAILED_TERMINAL -> TurnStatus.FAILED_TERMINAL;
            case FAILED -> TurnStatus.FAILED;
            case BLOCKED_BY_HUMAN -> TurnStatus.BLOCKED_BY_HUMAN;
        };
    }

    private static boolean isRetryableHermesFailure(RuntimeException exception) {
        if (exception instanceof br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway.HermesSessionsException hermesException) {
            int status = hermesException.status();
            return status == 0 || status == 429 || status >= 500;
        }
        String message = exception.getMessage();
        if (message == null) {
            return exception.getCause() instanceof java.util.concurrent.TimeoutException;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("timeout") || normalized.contains("timed out")
                || normalized.contains("temporar") || normalized.contains("connection reset")
                || normalized.contains("unavailable");
    }

    private void appendOutbound(ReceptionConversation conversation, String eventId,
                                String correlationId, String text) {
        ReceptionMessage message = new ReceptionMessage(UUID.randomUUID().toString(),
                ReceptionEventIds.outbound(eventId, correlationId), correlationId,
                conversation.id(), conversation.contactId(), ReceptionMessageDirection.OUTBOUND,
                ReceptionMessageSender.URBA, ReceptionMessageType.TEXT, text, null, null, clock.instant());
        transcript.appendIfAbsent(message);
    }

    private static boolean explicitTermsAcceptance(String text) {
        if (text == null) return false;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.matches(".*\\b(nao|nunca|jamais|nem)\\b.*")) {
            return false;
        }
        return normalized.matches(".*\\b(aceito|aceitar|concordo)\\b.*");
    }

    private TurnReceipt duplicateReceipt(InboundConversationEvent event, ReceptionMessage inbound) {
        Optional<ReceptionTurn> prior = turns.findByInboundMessageId(inbound.id());
        if (prior.isPresent()) {
            ReceptionTurn turn = prior.get();
            return new TurnReceipt(event.eventId(), turn.correlationId(), TurnStatus.DUPLICATE,
                    turn.output(), null);
        }
        // Explicit duplicate without a durable receipt is safer than
        // fabricating an action from current state.
        return new TurnReceipt(event.eventId(), inbound.correlationId(), TurnStatus.DUPLICATE, null,
                "event already processed");
    }

    public enum TurnStatus {
        QUEUED, RUNNING, DELAYED, RECONCILING, COMPLETED, DUPLICATE,
        BLOCKED_BY_HUMAN, FAILED, FAILED_RETRYABLE, FAILED_SAFE_TO_RETRY, FAILED_TERMINAL
    }

    public record TurnReceipt(String eventId, String correlationId, TurnStatus status,
                              AgentOutput output, String error) {
        public TurnReceipt {
            require(eventId, "eventId");
            require(correlationId, "correlationId");
            Objects.requireNonNull(status, "status");
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }

    public ReceptionMetrics metrics() {
        return metrics;
    }
}
