package br.com.urbana.connect.application.reception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * POC-only ingress pipeline. Media is normalized before events enter the
 * batching boundary, and only released batches reach the orchestrator.
 */
public final class PocReceptionIngress {
    private final ReceptionOrchestrator orchestrator;
    private final MessageBatcher batcher;
    private final MediaNormalizationService mediaNormalization;
    private final Object pipelineLock = new Object();
    private final Map<String, List<CompletableFuture<List<ReceptionOrchestrator.TurnReceipt>>>> inFlightByContact =
            new HashMap<>();

    public PocReceptionIngress(ReceptionOrchestrator orchestrator,
                               MessageBatcher batcher,
                               MediaNormalizationService mediaNormalization) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.batcher = Objects.requireNonNull(batcher, "batcher");
        this.mediaNormalization = Objects.requireNonNull(mediaNormalization, "mediaNormalization");
    }

    public ReceptionOrchestrator.TurnReceipt accept(InboundConversationEvent event) {
        InboundConversationEvent normalized = mediaNormalization.normalizeEvent(event);
        Optional<ReceptionOrchestrator.TurnReceipt> duplicate =
                orchestrator.duplicateReceiptIfFinalized(normalized);
        if (duplicate.isPresent()) {
            return duplicate.orElseThrow();
        }
        RegisteredRelease registered;
        synchronized (pipelineLock) {
            MessageBatcher.Release release = batcher.accept(normalized);
            registered = register(release.readyBatches());
        }
        List<ReceptionOrchestrator.TurnReceipt> receipts = process(registered);
        boolean currentEventWasReleased = registered.batches().stream()
                .flatMap(List::stream)
                .anyMatch(item -> item.eventId().equals(normalized.eventId()));
        if (!currentEventWasReleased) {
            return queued(normalized);
        }
        return receipts.getLast();
    }

    public List<ReceptionOrchestrator.TurnReceipt> flushDue(String contactId, Instant now) {
        RegisteredRelease registered;
        synchronized (pipelineLock) {
            registered = register(batcher.flushDue(contactId, now).readyBatches());
        }
        return process(registered);
    }

    public List<ReceptionOrchestrator.TurnReceipt> flushAllDue(Instant now) {
        RegisteredRelease registered;
        synchronized (pipelineLock) {
            registered = register(batcher.flushAllDue(now).readyBatches());
        }
        return process(registered);
    }

    public List<ReceptionOrchestrator.TurnReceipt> forceFlush(String contactId) {
        RegisteredRelease registered;
        List<CompletableFuture<List<ReceptionOrchestrator.TurnReceipt>>> inFlight;
        synchronized (pipelineLock) {
            registered = register(batcher.forceFlush(contactId).readyBatches());
            inFlight = registered.batches().isEmpty()
                    ? List.copyOf(inFlightByContact.getOrDefault(contactId, List.of()))
                    : List.of();
        }
        if (!registered.batches().isEmpty()) {
            return process(registered);
        }
        return await(inFlight);
    }

    private RegisteredRelease register(List<List<InboundConversationEvent>> batches) {
        if (batches == null || batches.isEmpty()) {
            return RegisteredRelease.empty();
        }
        Map<String, CompletableFuture<List<ReceptionOrchestrator.TurnReceipt>>> futures = new LinkedHashMap<>();
        for (List<InboundConversationEvent> batch : batches) {
            if (batch == null || batch.isEmpty()) {
                continue;
            }
            String contactId = batch.getFirst().contactId();
            futures.computeIfAbsent(contactId, ignored -> {
                CompletableFuture<List<ReceptionOrchestrator.TurnReceipt>> future = new CompletableFuture<>();
                inFlightByContact.computeIfAbsent(contactId, ignoredContact -> new ArrayList<>()).add(future);
                return future;
            });
        }
        return new RegisteredRelease(List.copyOf(batches), Map.copyOf(futures));
    }

    private List<ReceptionOrchestrator.TurnReceipt> process(RegisteredRelease registered) {
        if (registered.batches().isEmpty()) {
            return List.of();
        }
        Map<String, List<ReceptionOrchestrator.TurnReceipt>> receiptsByContact = new HashMap<>();
        List<ReceptionOrchestrator.TurnReceipt> receipts = new ArrayList<>();
        try {
            for (List<InboundConversationEvent> batch : registered.batches()) {
                ReceptionOrchestrator.TurnReceipt receipt = orchestrator.processBatch(batch);
                if (receipt.status() == ReceptionOrchestrator.TurnStatus.FAILED_RETRYABLE) {
                    // The orchestrator persists the inbound event and protects
                    // mutations with idempotency, so one immediate recovery is
                    // safe before exposing a retryable failure to the POC.
                    receipt = orchestrator.processBatch(batch);
                }
                receipts.add(receipt);
                receiptsByContact.computeIfAbsent(batch.getFirst().contactId(), ignored -> new ArrayList<>())
                        .add(receipt);
            }
            complete(registered, receiptsByContact, null);
            return List.copyOf(receipts);
        } catch (RuntimeException | Error exception) {
            complete(registered, Map.of(), exception);
            throw exception;
        }
    }

    private void complete(RegisteredRelease registered,
                          Map<String, List<ReceptionOrchestrator.TurnReceipt>> receiptsByContact,
                          Throwable failure) {
        for (Map.Entry<String, CompletableFuture<List<ReceptionOrchestrator.TurnReceipt>>> entry
                : registered.futuresByContact().entrySet()) {
            List<ReceptionOrchestrator.TurnReceipt> receipts = receiptsByContact.getOrDefault(entry.getKey(), List.of());
            if (failure == null) {
                entry.getValue().complete(List.copyOf(receipts));
            } else {
                entry.getValue().completeExceptionally(failure);
            }
            synchronized (pipelineLock) {
                List<CompletableFuture<List<ReceptionOrchestrator.TurnReceipt>>> futures =
                        inFlightByContact.get(entry.getKey());
                if (futures != null) {
                    futures.remove(entry.getValue());
                    if (futures.isEmpty()) {
                        inFlightByContact.remove(entry.getKey());
                    }
                }
            }
        }
    }

    private List<ReceptionOrchestrator.TurnReceipt> await(
            List<CompletableFuture<List<ReceptionOrchestrator.TurnReceipt>>> futures) {
        List<ReceptionOrchestrator.TurnReceipt> receipts = new ArrayList<>();
        for (CompletableFuture<List<ReceptionOrchestrator.TurnReceipt>> future : futures) {
            try {
                receipts.addAll(future.join());
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw exception;
            }
        }
        return List.copyOf(receipts);
    }

    private record RegisteredRelease(
            List<List<InboundConversationEvent>> batches,
            Map<String, CompletableFuture<List<ReceptionOrchestrator.TurnReceipt>>> futuresByContact) {
        private RegisteredRelease {
            batches = batches == null ? List.of() : List.copyOf(batches);
            futuresByContact = futuresByContact == null ? Map.of() : Map.copyOf(futuresByContact);
        }

        private static RegisteredRelease empty() {
            return new RegisteredRelease(List.of(), Map.of());
        }
    }

    private static ReceptionOrchestrator.TurnReceipt queued(InboundConversationEvent event) {
        return new ReceptionOrchestrator.TurnReceipt(event.eventId(), "queued:" + event.eventId(),
                ReceptionOrchestrator.TurnStatus.QUEUED, null, null);
    }
}
