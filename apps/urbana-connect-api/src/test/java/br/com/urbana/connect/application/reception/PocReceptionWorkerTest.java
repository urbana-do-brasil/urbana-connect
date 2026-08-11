package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.PocPendingEvent;
import br.com.urbana.connect.domain.reception.model.PocPendingEventStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.port.out.PocPendingEventGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class PocReceptionWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");
    private final List<PocReceptionWorker> workers = new ArrayList<>();

    @AfterEach
    void closeWorkers() {
        workers.forEach(PocReceptionWorker::close);
    }

    @Test
    void runsDifferentContactsInParallel() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            InboundConversationEvent event = invocation.<List<InboundConversationEvent>>getArgument(0).getFirst();
            return new ReceptionOrchestrator.TurnReceipt(event.eventId(), "corr-" + event.eventId(),
                    ReceptionOrchestrator.TurnStatus.COMPLETED,
                    new AgentOutput("ok", br.com.urbana.connect.domain.reception.model.AgentNextAction.AWAIT_CUSTOMER),
                    null);
        });
        MemoryPendingEvents pending = new MemoryPendingEvents();
        PocReceptionWorker worker = worker(orchestrator, pending, null);

        worker.enqueue(List.of(event("a-1", "poc:ana"), event("b-1", "poc:bia")));

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        worker.awaitIdle(Duration.ofSeconds(2));
        assertThat(pending.completed).containsExactlyInAnyOrder("a-1", "b-1");
    }

    @Test
    void forwardsOneDurableBatchAsOneOrchestratorInvocation() {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            List<InboundConversationEvent> batch = invocation.getArgument(0);
            assertThat(batch).extracting(InboundConversationEvent::eventId)
                    .containsExactly("batch-1", "batch-2");
            return new ReceptionOrchestrator.TurnReceipt("batch-2", "corr-batch",
                    ReceptionOrchestrator.TurnStatus.COMPLETED, null, null);
        });
        MemoryPendingEvents pending = new MemoryPendingEvents();
        PocReceptionWorker worker = worker(orchestrator, pending, null);

        worker.enqueueBatch(List.of(event("batch-1", "poc:ana"), event("batch-2", "poc:ana")));
        worker.awaitIdle(Duration.ofSeconds(2));

        verify(orchestrator).processBatch(argThat(batch -> batch.size() == 2
                && batch.getFirst().eventId().equals("batch-1")
                && batch.getLast().eventId().equals("batch-2")));
        assertThat(pending.completed).containsExactlyInAnyOrder("batch-1", "batch-2");
    }

    @Test
    void serializesSuccessiveEventsForOneContactAndPreservesOrder() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<String> order = new ArrayList<>();
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            String eventId = invocation.<List<InboundConversationEvent>>getArgument(0).getFirst().eventId();
            synchronized (order) {
                order.add(eventId);
            }
            if (eventId.equals("same-1")) {
                firstStarted.countDown();
                assertThat(releaseFirst.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return new ReceptionOrchestrator.TurnReceipt(eventId, "corr-" + eventId,
                    ReceptionOrchestrator.TurnStatus.COMPLETED, null, null);
        });
        MemoryPendingEvents pending = new MemoryPendingEvents();
        PocReceptionWorker worker = worker(orchestrator, pending, null);

        worker.enqueue(List.of(event("same-1", "poc:ana"), event("same-2", "poc:ana")));
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);
        synchronized (order) {
            assertThat(order).containsExactly("same-1");
        }
        releaseFirst.countDown();
        worker.awaitIdle(Duration.ofSeconds(2));
        assertThat(order).containsExactly("same-1", "same-2");
    }

    @Test
    void requeuesSafeFailureAndAllowsTheSameEventToBeRetried() {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        AtomicInteger calls = new AtomicInteger();
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            InboundConversationEvent event = invocation.<List<InboundConversationEvent>>getArgument(0).getFirst();
            if (calls.incrementAndGet() == 1) {
                return new ReceptionOrchestrator.TurnReceipt(event.eventId(), "corr-safe",
                        ReceptionOrchestrator.TurnStatus.FAILED_SAFE_TO_RETRY, null,
                        "HERMES_REJECTED_BEFORE_DISPATCH");
            }
            return new ReceptionOrchestrator.TurnReceipt(event.eventId(), "corr-safe",
                    ReceptionOrchestrator.TurnStatus.COMPLETED, null, null);
        });
        MemoryPendingEvents pending = new MemoryPendingEvents();
        PocReceptionWorker worker = worker(orchestrator, pending, null);
        InboundConversationEvent event = event("safe-retry", "poc:ana");

        worker.enqueue(List.of(event));
        worker.awaitIdle(Duration.ofSeconds(2));
        assertThat(pending.values.get(event.eventId()).status()).isEqualTo(PocPendingEventStatus.QUEUED);
        assertThat(pending.completed).isEmpty();

        worker.enqueue(List.of(event));
        worker.awaitIdle(Duration.ofSeconds(2));

        verify(orchestrator, times(2)).processBatch(anyList());
        assertThat(pending.completed).containsExactly(event.eventId());
    }

    @Test
    void doesNotRedispatchAnEventThatIsAlreadyCompleted() {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            InboundConversationEvent event = invocation.<List<InboundConversationEvent>>getArgument(0).getFirst();
            return new ReceptionOrchestrator.TurnReceipt(event.eventId(), "corr-complete",
                    ReceptionOrchestrator.TurnStatus.COMPLETED, null, null);
        });
        MemoryPendingEvents pending = new MemoryPendingEvents();
        PocReceptionWorker worker = worker(orchestrator, pending, null);
        InboundConversationEvent event = event("already-complete", "poc:ana");

        worker.enqueue(List.of(event));
        worker.awaitIdle(Duration.ofSeconds(2));
        worker.enqueue(List.of(event));
        worker.awaitIdle(Duration.ofSeconds(2));

        verify(orchestrator, times(1)).processBatch(anyList());
        assertThat(pending.completed).containsExactly(event.eventId());
    }

    @Test
    void recoversAClaimedEventThroughReconciliationWithoutRedispatch() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        ReceptionTurnReconciliationService reconciliation = mock(ReceptionTurnReconciliationService.class);
        MemoryPendingEvents pending = new MemoryPendingEvents();
        pending.save(PocPendingEvent.claimed(event("stale-1", "poc:ana"), "old-claim", NOW.minusSeconds(600)));
        when(reconciliation.reconcile("stale-1")).thenReturn(Optional.empty());
        PocReceptionWorker worker = worker(orchestrator, pending, reconciliation);

        worker.recover();
        worker.awaitIdle(Duration.ofSeconds(2));

        org.mockito.Mockito.verify(reconciliation).reconcile("stale-1");
        org.mockito.Mockito.verifyNoInteractions(orchestrator);
        assertThat(pending.completed).isEmpty();
    }

    @Test
    void leavesSuccessorQueuedWhileThePredecessorIsStillReconciling() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        ReceptionTurnReconciliationService reconciliation = mock(ReceptionTurnReconciliationService.class);
        CountDownLatch predecessorStarted = new CountDownLatch(1);
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            InboundConversationEvent event = invocation.<List<InboundConversationEvent>>getArgument(0).getFirst();
            if (event.eventId().equals("predecessor")) {
                predecessorStarted.countDown();
            }
            return new ReceptionOrchestrator.TurnReceipt(event.eventId(), "turn-predecessor",
                    ReceptionOrchestrator.TurnStatus.RECONCILING, null, "ambiguous");
        });
        when(reconciliation.reconcile(anyString())).thenReturn(Optional.empty());
        MemoryPendingEvents pending = new MemoryPendingEvents();
        PocReceptionWorker worker = worker(orchestrator, pending, reconciliation);

        worker.enqueue(List.of(event("predecessor", "poc:ana")));
        assertThat(predecessorStarted.await(1, TimeUnit.SECONDS)).isTrue();
        worker.enqueue(List.of(event("successor", "poc:ana")));

        Thread.sleep(150);

        assertThat(pending.values.get("successor")).isNotNull();
        assertThat(pending.values.get("successor").status()).isEqualTo(
                PocPendingEventStatus.QUEUED);
        verify(orchestrator).processBatch(argThat(batch -> batch.size() == 1
                && batch.getFirst().eventId().equals("predecessor")));
        verify(orchestrator, never()).processBatch(argThat(batch -> batch.size() == 1
                && batch.getFirst().eventId().equals("successor")));
    }

    private PocReceptionWorker worker(ReceptionOrchestrator orchestrator, MemoryPendingEvents pending,
                                      ReceptionTurnReconciliationService reconciliation) {
        PocReceptionWorker worker = new PocReceptionWorker(orchestrator, pending,
                reconciliation == null ? mock(ReceptionTurnReconciliationService.class) : reconciliation,
                2, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
        workers.add(worker);
        return worker;
    }

    private static InboundConversationEvent event(String id, String contact) {
        return new InboundConversationEvent(id, contact, ReceptionMessageType.TEXT, "Oi", NOW);
    }

    private static final class MemoryPendingEvents implements PocPendingEventGateway {
        private final Map<String, PocPendingEvent> values = new java.util.concurrent.ConcurrentHashMap<>();
        private final List<String> completed = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public PocPendingEvent saveIfAbsent(PocPendingEvent event) {
            return values.putIfAbsent(event.eventId(), event) == null ? event : values.get(event.eventId());
        }

        @Override
        public Optional<PocPendingEvent> findByEventId(String eventId) {
            return Optional.ofNullable(values.get(eventId));
        }

        @Override
        public Optional<PocPendingEvent> claim(String eventId, String claimToken, Instant now, Duration leaseTtl) {
            PocPendingEvent[] claimed = new PocPendingEvent[1];
            values.computeIfPresent(eventId, (ignored, current) -> {
                if (!current.claimableAt(now, leaseTtl)) return current;
                claimed[0] = current.claim(claimToken, now);
                return claimed[0];
            });
            return Optional.ofNullable(claimed[0]);
        }

        @Override
        public List<PocPendingEvent> findRecoverable(Instant now, Duration leaseTtl) {
            return values.values().stream().filter(event -> event.claimableAt(now, leaseTtl)).toList();
        }

        @Override
        public List<PocPendingEvent> findByContactId(String contactId) {
            return values.values().stream().filter(event -> event.contactId().equals(contactId)).toList();
        }

        @Override
        public Optional<PocPendingEvent> complete(String eventId, String claimToken, Instant now) {
            return Optional.ofNullable(values.computeIfPresent(eventId, (ignored, current) -> {
                if (current.status() != PocPendingEventStatus.CLAIMED
                        || !claimToken.equals(current.claimToken())) return current;
                completed.add(eventId);
                return current.complete(now);
            }));
        }

        @Override
        public Optional<PocPendingEvent> requeueIfRetrySafe(String eventId, String claimToken, Instant now) {
            PocPendingEvent[] requeued = new PocPendingEvent[1];
            values.computeIfPresent(eventId, (ignored, current) -> {
                if (current.status() != PocPendingEventStatus.CLAIMED
                        || !claimToken.equals(current.claimToken())) return current;
                requeued[0] = current.requeue(now);
                return requeued[0];
            });
            return Optional.ofNullable(requeued[0]);
        }
    }
}
