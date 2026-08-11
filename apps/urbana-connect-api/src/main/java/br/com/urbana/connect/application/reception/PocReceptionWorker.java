package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.PocPendingEvent;
import br.com.urbana.connect.domain.reception.model.PocPendingEventStatus;
import br.com.urbana.connect.domain.reception.port.out.PocPendingEventGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Local durable-queue worker with per-contact ordering and cross-contact parallelism. */
public class PocReceptionWorker implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PocReceptionWorker.class);
    private static final Duration DEFAULT_SUCCESSOR_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Comparator<PocPendingEvent> PENDING_ORDER = Comparator
            .comparing(PocPendingEvent::contactId)
            .thenComparing(PocPendingEvent::occurredAt)
            .thenComparing(PocPendingEvent::acceptedAt)
            .thenComparing(PocPendingEvent::eventId);

    private final ReceptionOrchestrator orchestrator;
    private final PocPendingEventGateway pendingEvents;
    private final ReceptionTurnReconciliationService reconciliation;
    private final Clock clock;
    private final Duration claimTtl;
    private final Duration successorRetryDelay;
    private final ScheduledExecutorService executor;
    private final ConcurrentMap<String, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> blockedContacts = new ConcurrentHashMap<>();
    private final Set<String> scheduledEventIds = ConcurrentHashMap.newKeySet();

    public PocReceptionWorker(ReceptionOrchestrator orchestrator,
                              PocPendingEventGateway pendingEvents,
                              ReceptionTurnReconciliationService reconciliation,
                              int parallelism, Clock clock, Duration claimTtl) {
        this(orchestrator, pendingEvents, reconciliation, parallelism, clock, claimTtl,
                DEFAULT_SUCCESSOR_RETRY_DELAY);
    }

    public PocReceptionWorker(ReceptionOrchestrator orchestrator,
                              PocPendingEventGateway pendingEvents,
                              ReceptionTurnReconciliationService reconciliation,
                              int parallelism, Clock clock, Duration claimTtl,
                              Duration successorRetryDelay) {
        if (parallelism < 1) throw new IllegalArgumentException("parallelism must be positive");
        if (claimTtl == null || claimTtl.isZero() || claimTtl.isNegative()) {
            throw new IllegalArgumentException("claim ttl must be positive");
        }
        if (successorRetryDelay == null || successorRetryDelay.isZero() || successorRetryDelay.isNegative()) {
            throw new IllegalArgumentException("successor retry delay must be positive");
        }
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.pendingEvents = Objects.requireNonNull(pendingEvents, "pendingEvents");
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.claimTtl = claimTtl;
        this.successorRetryDelay = successorRetryDelay;
        this.executor = Executors.newScheduledThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(runnable, "poc-reception-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Compatibility entry point: each event is its own one-event batch. */
    public void enqueue(List<InboundConversationEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (InboundConversationEvent event : List.copyOf(events)) {
            enqueueBatch(List.of(event));
        }
    }

    /** Enqueues one durable batch and invokes the orchestrator once for that batch. */
    public void enqueueBatch(List<InboundConversationEvent> events) {
        if (events == null || events.isEmpty()) return;
        List<InboundConversationEvent> batch = normalizeBatch(events);
        for (InboundConversationEvent event : batch) {
            pendingEvents.saveIfAbsent(PocPendingEvent.accepted(event, clock.instant()));
        }
        scheduleBatch(batch);
    }

    public void submit(List<InboundConversationEvent> events) {
        enqueue(events);
    }

    public void submitBatch(List<InboundConversationEvent> events) {
        enqueueBatch(events);
    }

    /** Recovers queued events and expired claims in their durable order. */
    public void recover() {
        Instant now = clock.instant();
        pendingEvents.findRecoverable(now, claimTtl).stream()
                .sorted(PENDING_ORDER)
                .forEach(this::scheduleRecovered);
    }

    /** Compatibility entry point for callers that only want expired claims. */
    public void recoverClaimed() {
        Instant now = clock.instant();
        pendingEvents.findRecoverable(now, claimTtl).stream()
                .filter(event -> event.status() == PocPendingEventStatus.CLAIMED)
                .sorted(PENDING_ORDER)
                .forEach(this::scheduleRecovered);
    }

    public void awaitIdle(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (tails.values().stream().allMatch(CompletableFuture::isDone)) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("POC worker did not become idle before timeout");
    }

    private void scheduleRecovered(PocPendingEvent event) {
        if (event.status() == PocPendingEventStatus.CLAIMED) {
            if (!reserve(event.eventId())) return;
            schedule(event.contactId(), () -> {
                release(event.eventId());
                reconcileClaimed(event);
            });
            return;
        }
        scheduleBatch(List.of(event.event()));
    }

    private void scheduleBatch(List<InboundConversationEvent> batch) {
        if (!reserve(batch)) return;
        String contactId = batch.getFirst().contactId();
        schedule(contactId, () -> {
            release(batch);
            processBatch(batch);
        });
    }

    private void scheduleBatchAfter(List<InboundConversationEvent> batch) {
        if (!reserve(batch)) return;
        String contactId = batch.getFirst().contactId();
        scheduleAfter(contactId, () -> {
            release(batch);
            processBatch(batch);
        });
    }

    private void processBatch(List<InboundConversationEvent> batch) {
        String contactId = batch.getFirst().contactId();
        String knownBlocker = blockingContact(contactId);
        String olderClaim = findOlderClaimed(contactId, batch);
        if (knownBlocker != null || olderClaim != null) {
            block(contactId, knownBlocker == null ? olderClaim : knownBlocker);
            scheduleBatchAfter(batch);
            return;
        }

        String claimToken = UUID.randomUUID().toString();
        List<ClaimedEvent> claims = new ArrayList<>();
        List<InboundConversationEvent> input = new ArrayList<>();
        try {
            for (InboundConversationEvent event : batch) {
                Optional<PocPendingEvent> claimed = pendingEvents.claim(
                        event.eventId(), claimToken, clock.instant(), claimTtl);
                if (claimed.isPresent() && claimed.orElseThrow().status() == PocPendingEventStatus.CLAIMED
                        && claimToken.equals(claimed.orElseThrow().claimToken())) {
                    claims.add(new ClaimedEvent(event, claimToken));
                    input.add(event);
                    continue;
                }

                Optional<PocPendingEvent> current = pendingEvents.findByEventId(event.eventId());
                if (current.filter(item -> item.status() == PocPendingEventStatus.COMPLETED).isPresent()) {
                    // Keep a finalized duplicate in a mixed batch so the
                    // orchestrator can apply its normal idempotency rules.
                    input.add(event);
                    continue;
                }
                requeue(claims);
                String blocker = current.filter(item -> item.status() == PocPendingEventStatus.CLAIMED)
                        .map(PocPendingEvent::eventId).orElse(null);
                if (blocker != null) block(contactId, blocker);
                scheduleBatchAfter(batch);
                return;
            }
            if (claims.isEmpty()) return;

            ReceptionOrchestrator.TurnReceipt receipt = Objects.requireNonNull(
                    orchestrator.processBatch(List.copyOf(input)), "orchestrator receipt");
            handleReceipt(batch, claims, receipt);
        } catch (RuntimeException failure) {
            // A missing receipt is ambiguous. Keep the claim fenced and let
            // reconciliation/recovery decide; never dispatch a second turn here.
            LOGGER.warn("POC reception worker failed before durable resolution eventId={}",
                    batch.getFirst().eventId(), failure);
            block(contactId, batch.getFirst().eventId());
        }
    }

    private void handleReceipt(List<InboundConversationEvent> batch,
                               List<ClaimedEvent> claims,
                               ReceptionOrchestrator.TurnReceipt receipt) {
        String contactId = batch.getFirst().contactId();
        switch (receipt.status()) {
            case RECONCILING -> {
                Optional<String> output = tryReconcile(receipt.eventId());
                if (output.isPresent()) {
                    complete(claims);
                    clearBlock(contactId, batch.getFirst().eventId());
                    return;
                }
                String predecessor = findOlderClaimed(contactId, batch);
                if (predecessor != null) {
                    requeue(claims);
                    block(contactId, predecessor);
                    scheduleBatchAfter(batch);
                } else {
                    block(contactId, batch.getFirst().eventId());
                }
            }
            case RUNNING, DELAYED, QUEUED -> {
                block(contactId, batch.getFirst().eventId());
            }
            case FAILED_SAFE_TO_RETRY -> requeue(claims);
            default -> complete(claims);
        }
    }

    private void reconcileClaimed(PocPendingEvent event) {
        String contactId = event.contactId();
        String claimToken = UUID.randomUUID().toString();
        PocPendingEvent claimed = pendingEvents.claim(event.eventId(), claimToken, clock.instant(), claimTtl)
                .orElse(null);
        if (claimed == null) {
            return;
        }
        Optional<String> output = tryReconcile(event.eventId());
        if (output.isPresent()) {
            pendingEvents.complete(event.eventId(), claimToken, clock.instant());
            clearBlock(contactId, event.eventId());
            return;
        }
        // Keep the claim recoverable. The scheduled recovery cycle will try
        // again only after the claim TTL, preventing a hot loop and preserving
        // the no-redispatch guarantee for an ambiguous remote execution.
        block(contactId, event.eventId());
    }

    private Optional<String> tryReconcile(String turnOrEventId) {
        try {
            return reconciliation.reconcile(turnOrEventId);
        } catch (RuntimeException failure) {
            LOGGER.warn("POC reception reconciliation failed id={}", turnOrEventId, failure);
            return Optional.empty();
        }
    }

    private void complete(List<ClaimedEvent> claims) {
        Instant now = clock.instant();
        for (ClaimedEvent claim : claims) {
            pendingEvents.complete(claim.event().eventId(), claim.claimToken(), now);
        }
    }

    private void requeue(List<ClaimedEvent> claims) {
        Instant now = clock.instant();
        for (ClaimedEvent claim : claims) {
            pendingEvents.requeueIfRetrySafe(claim.event().eventId(), claim.claimToken(), now);
        }
    }

    private String blockingContact(String contactId) {
        String blocker = blockedContacts.get(contactId);
        if (blocker == null) return null;
        Optional<PocPendingEvent> current = pendingEvents.findByEventId(blocker);
        if (current.isPresent() && current.orElseThrow().status() == PocPendingEventStatus.CLAIMED) {
            return blocker;
        }
        blockedContacts.remove(contactId, blocker);
        return null;
    }

    private String findOlderClaimed(String contactId, List<InboundConversationEvent> batch) {
        Set<String> batchIds = batch.stream().map(InboundConversationEvent::eventId).collect(java.util.stream.Collectors.toSet());
        InboundConversationEvent first = batch.getFirst();
        return pendingEvents.findByContactId(contactId).stream()
                .filter(event -> event.status() == PocPendingEventStatus.CLAIMED)
                .filter(event -> !batchIds.contains(event.eventId()))
                .filter(event -> compare(event, first) < 0)
                .min(PENDING_ORDER)
                .map(PocPendingEvent::eventId)
                .orElse(null);
    }

    private static int compare(PocPendingEvent pending, InboundConversationEvent event) {
        int contact = pending.contactId().compareTo(event.contactId());
        if (contact != 0) return contact;
        int occurred = pending.occurredAt().compareTo(event.occurredAt());
        if (occurred != 0) return occurred;
        return pending.eventId().compareTo(event.eventId());
    }

    private void block(String contactId, String eventId) {
        if (eventId != null) blockedContacts.put(contactId, eventId);
    }

    private void clearBlock(String contactId, String eventId) {
        if (eventId == null) blockedContacts.remove(contactId);
        else blockedContacts.remove(contactId, eventId);
    }

    private boolean reserve(String eventId) {
        synchronized (scheduledEventIds) {
            if (scheduledEventIds.contains(eventId)) return false;
            scheduledEventIds.add(eventId);
            return true;
        }
    }

    private boolean reserve(List<InboundConversationEvent> batch) {
        synchronized (scheduledEventIds) {
            if (batch.stream().map(InboundConversationEvent::eventId).anyMatch(scheduledEventIds::contains)) {
                return false;
            }
            batch.forEach(event -> scheduledEventIds.add(event.eventId()));
            return true;
        }
    }

    private void release(String eventId) {
        synchronized (scheduledEventIds) {
            scheduledEventIds.remove(eventId);
        }
    }

    private void release(List<InboundConversationEvent> batch) {
        synchronized (scheduledEventIds) {
            batch.forEach(event -> scheduledEventIds.remove(event.eventId()));
        }
    }

    private void schedule(String contactId, Runnable action) {
        tails.compute(contactId, (ignored, previous) -> {
            CompletableFuture<Void> predecessor = previous == null
                    ? CompletableFuture.completedFuture(null) : previous.handle((value, failure) -> null);
            CompletableFuture<Void> next = predecessor.thenRunAsync(action, executor);
            next.whenComplete((value, failure) -> tails.remove(contactId, next));
            return next;
        });
    }

    private void scheduleAfter(String contactId, Runnable action) {
        long delayMillis = Math.max(1, successorRetryDelay.toMillis());
        tails.compute(contactId, (ignored, previous) -> {
            CompletableFuture<Void> predecessor = previous == null
                    ? CompletableFuture.completedFuture(null) : previous.handle((value, failure) -> null);
            CompletableFuture<Void> next = predecessor.thenRunAsync(action,
                    CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS, executor));
            next.whenComplete((value, failure) -> tails.remove(contactId, next));
            return next;
        });
    }

    private static List<InboundConversationEvent> normalizeBatch(List<InboundConversationEvent> events) {
        List<InboundConversationEvent> batch = List.copyOf(events);
        String contactId = batch.getFirst().contactId();
        if (batch.stream().anyMatch(event -> !contactId.equals(event.contactId()))) {
            throw new IllegalArgumentException("a durable batch cannot contain multiple contacts");
        }
        return batch;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private record ClaimedEvent(InboundConversationEvent event, String claimToken) {
    }

    private record ClaimedBatch(String contactId, List<ClaimedEvent> events) {
        private ClaimedBatch {
            events = List.copyOf(events);
        }
    }
}
