package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Groups nearby text fragments per contact while keeping interactive commands
 * and payment evidence immediately actionable.
 */
public final class MessageBatcher {
    public static final Duration MOVING_WINDOW = Duration.ofSeconds(4);
    public static final Duration MAX_BATCH_AGE = Duration.ofSeconds(10);

    private final Map<String, PendingBatch> pendingByContact = new HashMap<>();

    public synchronized Release accept(InboundConversationEvent event) {
        Objects.requireNonNull(event, "event");
        PendingBatch pending = pendingByContact.get(event.contactId());

        if (isImmediate(event)) {
            List<List<InboundConversationEvent>> ready = new ArrayList<>();
            if (pending != null) {
                ready.add(pending.events());
                pendingByContact.remove(event.contactId());
            }
            ready.add(List.of(event));
            return new Release(ready);
        }

        if (pending == null) {
            pendingByContact.put(event.contactId(), PendingBatch.start(event));
            return Release.empty();
        }

        if (canJoin(pending, event)) {
            pending.add(event);
            return Release.empty();
        }

        pendingByContact.put(event.contactId(), PendingBatch.start(event));
        return new Release(List.of(pending.events()));
    }

    /**
     * Flushes a batch only after the moving window or the ten-second cap has
     * elapsed. A caller can invoke this from a scheduler or before polling.
     */
    public synchronized Release flushDue(String contactId, Instant now) {
        requireContact(contactId);
        Objects.requireNonNull(now, "now");
        PendingBatch pending = pendingByContact.get(contactId);
        if (pending == null || !pending.isDue(now)) {
            return Release.empty();
        }
        pendingByContact.remove(contactId);
        return new Release(List.of(pending.events()));
    }

    /** Releases a contact's pending batch regardless of age for deterministic POC control. */
    public synchronized Release forceFlush(String contactId) {
        requireContact(contactId);
        PendingBatch pending = pendingByContact.remove(contactId);
        return pending == null ? Release.empty() : new Release(List.of(pending.events()));
    }

    public synchronized Release flushAllDue(Instant now) {
        Objects.requireNonNull(now, "now");
        List<List<InboundConversationEvent>> ready = new ArrayList<>();
        for (String contactId : List.copyOf(pendingByContact.keySet())) {
            ready.addAll(flushDue(contactId, now).readyBatches());
        }
        return new Release(ready);
    }

    public synchronized int pendingContacts() {
        return pendingByContact.size();
    }

    private static boolean canJoin(PendingBatch pending, InboundConversationEvent event) {
        Instant at = event.occurredAt();
        return !at.isAfter(pending.lastAt().plus(MOVING_WINDOW))
                && !at.isAfter(pending.firstAt().plus(MAX_BATCH_AGE))
                && !at.isBefore(pending.firstAt());
    }

    private static boolean isImmediate(InboundConversationEvent event) {
        ReceptionMessageType type = event.type();
        return type == ReceptionMessageType.INTERACTIVE
                || type == ReceptionMessageType.PAYMENT_PROOF
                || event.isPaymentProof();
    }

    private static void requireContact(String contactId) {
        if (contactId == null || contactId.isBlank()) {
            throw new IllegalArgumentException("contactId must not be blank");
        }
    }

    public record Release(List<List<InboundConversationEvent>> readyBatches) {
        public Release {
            readyBatches = readyBatches == null ? List.of() : readyBatches.stream()
                    .map(batch -> List.copyOf(batch))
                    .toList();
        }

        public static Release empty() {
            return new Release(Collections.emptyList());
        }
    }

    private static final class PendingBatch {
        private final Instant firstAt;
        private Instant lastAt;
        private final List<InboundConversationEvent> events;
        private final Set<String> eventIds;

        private PendingBatch(InboundConversationEvent first) {
            this.firstAt = first.occurredAt();
            this.lastAt = first.occurredAt();
            this.events = new ArrayList<>(List.of(first));
            this.eventIds = new HashSet<>(Set.of(first.eventId()));
        }

        static PendingBatch start(InboundConversationEvent event) {
            return new PendingBatch(event);
        }

        void add(InboundConversationEvent event) {
            if (!eventIds.add(event.eventId())) {
                return;
            }
            events.add(event);
            lastAt = event.occurredAt();
        }

        List<InboundConversationEvent> events() {
            return List.copyOf(events);
        }

        Instant firstAt() {
            return firstAt;
        }

        Instant lastAt() {
            return lastAt;
        }

        boolean isDue(Instant now) {
            return !now.isBefore(lastAt.plus(MOVING_WINDOW))
                    || !now.isBefore(firstAt.plus(MAX_BATCH_AGE));
        }
    }
}
