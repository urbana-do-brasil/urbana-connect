package br.com.urbana.connect.domain.reception.model;

import br.com.urbana.connect.application.reception.InboundConversationEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Durable representation of a synthetic POC input before turn execution. */
public record PocPendingEvent(
        String eventId,
        String contactId,
        ReceptionMessageType type,
        String text,
        String transcript,
        String mediaFixture,
        String interactiveReplyId,
        Instant occurredAt,
        String providerMessageId,
        Instant acceptedAt,
        PocPendingEventStatus status,
        String claimToken,
        Instant claimedAt,
        Instant completedAt) {

    public PocPendingEvent {
        require(eventId, "eventId");
        require(contactId, "contactId");
        type = Objects.requireNonNull(type, "type");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
        status = Objects.requireNonNull(status, "status");
        if (status == PocPendingEventStatus.CLAIMED && (claimToken == null || claimToken.isBlank())) {
            throw new IllegalArgumentException("claimed event requires claimToken");
        }
    }

    public static PocPendingEvent accepted(InboundConversationEvent event, Instant acceptedAt) {
        Objects.requireNonNull(event, "event");
        return new PocPendingEvent(event.eventId(), event.contactId(), event.type(), event.text(),
                event.transcript(), event.mediaFixture(), event.interactiveReplyId(), event.occurredAt(),
                event.providerMessageId(), acceptedAt, PocPendingEventStatus.QUEUED, null, null, null);
    }

    public static PocPendingEvent claimed(InboundConversationEvent event, String claimToken, Instant claimedAt) {
        PocPendingEvent accepted = accepted(event, claimedAt);
        return accepted.claim(claimToken, claimedAt);
    }

    public InboundConversationEvent event() {
        return new InboundConversationEvent(eventId, contactId, type, text, transcript, mediaFixture,
                interactiveReplyId, occurredAt, providerMessageId);
    }

    public boolean claimableAt(Instant now, Duration leaseTtl) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseTtl, "leaseTtl");
        if (status == PocPendingEventStatus.QUEUED) return true;
        return status == PocPendingEventStatus.CLAIMED
                && claimedAt != null && !now.isBefore(claimedAt.plus(leaseTtl));
    }

    public PocPendingEvent claim(String nextClaimToken, Instant now) {
        require(nextClaimToken, "claimToken");
        Objects.requireNonNull(now, "now");
        if (status == PocPendingEventStatus.COMPLETED || status == PocPendingEventStatus.ABANDONED) {
            return this;
        }
        return new PocPendingEvent(eventId, contactId, type, text, transcript, mediaFixture,
                interactiveReplyId, occurredAt, providerMessageId, acceptedAt,
                PocPendingEventStatus.CLAIMED, nextClaimToken, now, completedAt);
    }

    public PocPendingEvent complete(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == PocPendingEventStatus.COMPLETED) return this;
        return new PocPendingEvent(eventId, contactId, type, text, transcript, mediaFixture,
                interactiveReplyId, occurredAt, providerMessageId, acceptedAt,
                PocPendingEventStatus.COMPLETED, claimToken, claimedAt, now);
    }

    /** Returns a claim to the durable queue after a retry was proven safe. */
    public PocPendingEvent requeue(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status != PocPendingEventStatus.CLAIMED) return this;
        return new PocPendingEvent(eventId, contactId, type, text, transcript, mediaFixture,
                interactiveReplyId, occurredAt, providerMessageId, acceptedAt,
                PocPendingEventStatus.QUEUED, null, null, null);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
