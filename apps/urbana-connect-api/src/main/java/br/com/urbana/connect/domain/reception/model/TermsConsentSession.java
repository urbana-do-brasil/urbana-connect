package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Ephemeral, single-purpose terms presentation bound to one contracting unit.
 * The bearer token is intentionally absent; only its digest is stored.
 */
public record TermsConsentSession(
        String presentationId,
        String conversationId,
        String contactId,
        String destinationRef,
        String contractingUnitId,
        String environment,
        String serviceType,
        String termsVersion,
        String termsHash,
        String termsResource,
        String termsContent,
        String tokenDigest,
        Instant issuedAt,
        Instant expiresAt,
        TermsConsentSessionStatus status,
        Instant pagePresentedAt,
        Instant endReachedAt,
        TermsConsentDecision decision,
        Instant decidedAt,
        String decisionEventId,
        long version) {

    /** Compatibility constructor for adapters that do not expose a revision. */
    public TermsConsentSession(String presentationId, String conversationId, String contactId,
                               String destinationRef, String contractingUnitId, String environment,
                               String serviceType, String termsVersion, String termsHash,
                               String termsResource, String termsContent, String tokenDigest,
                               Instant issuedAt, Instant expiresAt, TermsConsentSessionStatus status,
                               Instant pagePresentedAt, Instant endReachedAt, TermsConsentDecision decision,
                               Instant decidedAt, String decisionEventId) {
        this(presentationId, conversationId, contactId, destinationRef, contractingUnitId, environment,
                serviceType, termsVersion, termsHash, termsResource, termsContent, tokenDigest, issuedAt,
                expiresAt, status, pagePresentedAt, endReachedAt, decision, decidedAt, decisionEventId, 0L);
    }

    public TermsConsentSession {
        require(presentationId, "presentationId");
        require(conversationId, "conversationId");
        require(contactId, "contactId");
        require(destinationRef, "destinationRef");
        require(contractingUnitId, "contractingUnitId");
        require(environment, "environment");
        require(serviceType, "serviceType");
        require(termsVersion, "termsVersion");
        require(termsHash, "termsHash");
        require(termsResource, "termsResource");
        require(termsContent, "termsContent");
        require(tokenDigest, "tokenDigest");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        status = Objects.requireNonNull(status, "status");
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (pagePresentedAt != null && pagePresentedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("pagePresentedAt cannot precede issuedAt");
        }
        if (endReachedAt != null && pagePresentedAt == null) {
            throw new IllegalArgumentException("endReachedAt requires page presentation");
        }
        if (endReachedAt != null && pagePresentedAt != null && endReachedAt.isBefore(pagePresentedAt)) {
            throw new IllegalArgumentException("endReachedAt cannot precede page presentation");
        }
        if (decidedAt != null && endReachedAt != null && decidedAt.isBefore(endReachedAt)) {
            throw new IllegalArgumentException("decidedAt cannot precede end reached");
        }
        if (status == TermsConsentSessionStatus.ACCEPTED || status == TermsConsentSessionStatus.DECLINED) {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(decidedAt, "decidedAt");
            require(decisionEventId, "decisionEventId");
        }
        if (decision != null && status != TermsConsentSessionStatus.ACCEPTED
                && status != TermsConsentSessionStatus.DECLINED) {
            throw new IllegalArgumentException("decision requires a terminal decision status");
        }
    }

    public static TermsConsentSession issued(String presentationId, String conversationId,
                                             String contactId, String destinationRef,
                                             String contractingUnitId, String environment,
                                             String serviceType, String termsVersion, String termsHash,
                                             String termsResource, String termsContent, String tokenDigest,
                                             Instant issuedAt, Instant expiresAt) {
        return new TermsConsentSession(presentationId, conversationId, contactId, destinationRef,
                contractingUnitId, environment, serviceType, termsVersion, termsHash, termsResource,
                termsContent, tokenDigest, issuedAt, expiresAt, TermsConsentSessionStatus.ISSUED,
                null, null, null, null, null, 0L);
    }

    public boolean expiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(expiresAt);
    }

    public TermsConsentSession pagePresented(Instant now) {
        Objects.requireNonNull(now, "now");
        ensureLive(now);
        if (status == TermsConsentSessionStatus.PAGE_PRESENTED
                || status == TermsConsentSessionStatus.END_REACHED) {
            return this;
        }
        ensureStatus(TermsConsentSessionStatus.ISSUED, "page presentation");
        return copy(TermsConsentSessionStatus.PAGE_PRESENTED, now, endReachedAt,
                decision, decidedAt, decisionEventId);
    }

    public TermsConsentSession endReached(Instant now) {
        Objects.requireNonNull(now, "now");
        ensureLive(now);
        if (status == TermsConsentSessionStatus.END_REACHED) {
            return this;
        }
        ensureStatus(TermsConsentSessionStatus.PAGE_PRESENTED, "end reached");
        return copy(TermsConsentSessionStatus.END_REACHED, pagePresentedAt, now,
                decision, decidedAt, decisionEventId);
    }

    public TermsConsentSession accept(Instant now, String eventId) {
        return decide(TermsConsentDecision.ACCEPT, now, eventId);
    }

    public TermsConsentSession decline(Instant now, String eventId) {
        return decide(TermsConsentDecision.DECLINE, now, eventId);
    }

    public TermsConsentSession expire(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == TermsConsentSessionStatus.EXPIRED) {
            return this;
        }
        if (!expiredAt(now)) {
            throw new IllegalStateException("terms session cannot expire before its deadline");
        }
        if (status == TermsConsentSessionStatus.ACCEPTED
                || status == TermsConsentSessionStatus.DECLINED) {
            return this;
        }
        return copy(TermsConsentSessionStatus.EXPIRED, pagePresentedAt, endReachedAt,
                null, null, null);
    }

    public TermsConsentSession invalidate(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == TermsConsentSessionStatus.ACCEPTED
                || status == TermsConsentSessionStatus.DECLINED) {
            throw new IllegalStateException("a decided terms session cannot be invalidated");
        }
        if (status == TermsConsentSessionStatus.INVALIDATED) {
            return this;
        }
        return copy(TermsConsentSessionStatus.INVALIDATED, pagePresentedAt, endReachedAt,
                null, null, null);
    }

    private TermsConsentSession decide(TermsConsentDecision nextDecision, Instant now, String eventId) {
        Objects.requireNonNull(now, "now");
        require(eventId, "decisionEventId");
        if (status == TermsConsentSessionStatus.ACCEPTED
                || status == TermsConsentSessionStatus.DECLINED) {
            if (decision == nextDecision) {
                return this;
            }
            throw new IllegalStateException("opposite terms decision already recorded");
        }
        ensureLive(now);
        if (nextDecision == TermsConsentDecision.ACCEPT && status != TermsConsentSessionStatus.END_REACHED) {
            throw new IllegalStateException("ACCEPT requires END_REACHED");
        }
        if (nextDecision == TermsConsentDecision.DECLINE
                && status != TermsConsentSessionStatus.PAGE_PRESENTED
                && status != TermsConsentSessionStatus.END_REACHED) {
            throw new IllegalStateException("DECLINE requires page presentation");
        }
        return copy(nextDecision == TermsConsentDecision.ACCEPT
                        ? TermsConsentSessionStatus.ACCEPTED : TermsConsentSessionStatus.DECLINED,
                pagePresentedAt, endReachedAt, nextDecision, now, eventId);
    }

    private void ensureLive(Instant now) {
        if (status == TermsConsentSessionStatus.EXPIRED || status == TermsConsentSessionStatus.INVALIDATED) {
            throw new IllegalStateException("terms session is no longer active");
        }
        if (expiredAt(now)) {
            throw new IllegalStateException("terms session has expired");
        }
    }

    private void ensureStatus(TermsConsentSessionStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(action + " requires " + expected);
        }
    }

    private TermsConsentSession copy(TermsConsentSessionStatus nextStatus, Instant nextPagePresentedAt,
                                     Instant nextEndReachedAt, TermsConsentDecision nextDecision,
                                     Instant nextDecidedAt, String nextDecisionEventId) {
        return new TermsConsentSession(presentationId, conversationId, contactId, destinationRef,
                contractingUnitId, environment, serviceType, termsVersion, termsHash, termsResource,
                termsContent, tokenDigest, issuedAt, expiresAt, nextStatus, nextPagePresentedAt,
                nextEndReachedAt, nextDecision, nextDecidedAt, nextDecisionEventId, version + 1);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
