package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable evidence for one resource presented to one contracting unit.  The
 * first accepted inbound event wins; later events cannot rewrite its text.
 */
public record TermsConsentAudit(
        String presentationId, String conversationId, String contactId, String turnId,
        String contractingUnitId, String environmentLabelSnapshot, String environmentSourceMessageId,
        String serviceType, String termsResource, String termsVersion, String prepareTermsInvocationId,
        String termsOutboundMessageId, Instant presentedAt, String acceptanceMessageId,
        String acceptanceEventId, String acceptanceTextExact, Instant acceptedAt, Instant recordedAt,
        TermsConsentStatus status, long conversationVersionAtPresentation,
        Long conversationVersionAtAcceptance) {

    public TermsConsentAudit {
        require(presentationId, "presentationId"); require(conversationId, "conversationId");
        require(contactId, "contactId"); require(turnId, "turnId"); require(contractingUnitId, "contractingUnitId");
        require(environmentLabelSnapshot, "environmentLabelSnapshot");
        require(environmentSourceMessageId, "environmentSourceMessageId"); require(serviceType, "serviceType");
        require(termsResource, "termsResource"); require(prepareTermsInvocationId, "prepareTermsInvocationId");
        require(termsOutboundMessageId, "termsOutboundMessageId");
        presentedAt = Objects.requireNonNull(presentedAt, "presentedAt");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        status = Objects.requireNonNull(status, "status");
        if (conversationVersionAtPresentation < 0) throw new IllegalArgumentException("presentation version must be non-negative");
        if (status == TermsConsentStatus.PRESENTED && (acceptanceMessageId != null || acceptanceEventId != null
                || acceptanceTextExact != null || acceptedAt != null || conversationVersionAtAcceptance != null)) {
            throw new IllegalArgumentException("presented audit cannot contain acceptance evidence");
        }
        if (status == TermsConsentStatus.ACCEPTED) {
            require(acceptanceMessageId, "acceptanceMessageId"); require(acceptanceEventId, "acceptanceEventId");
            require(acceptanceTextExact, "acceptanceTextExact");
            acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
            if (conversationVersionAtAcceptance == null || conversationVersionAtAcceptance < 0) {
                throw new IllegalArgumentException("acceptance version must be non-negative");
            }
        }
    }

    public TermsConsentAudit accept(String messageId, String eventId, String exactText,
                                    Instant now, long conversationVersion) {
        if (status == TermsConsentStatus.ACCEPTED) return this;
        require(messageId, "acceptanceMessageId");
        require(eventId, "acceptanceEventId");
        require(exactText, "acceptanceTextExact");
        Objects.requireNonNull(now, "acceptedAt");
        if (now.isBefore(presentedAt)) {
            throw new IllegalStateException("terms acceptance cannot precede presentation");
        }
        if (conversationVersion < 0) {
            throw new IllegalArgumentException("acceptance version must be non-negative");
        }
        return new TermsConsentAudit(presentationId, conversationId, contactId, turnId, contractingUnitId,
                environmentLabelSnapshot, environmentSourceMessageId, serviceType, termsResource, termsVersion,
                prepareTermsInvocationId, termsOutboundMessageId, presentedAt, messageId, eventId, exactText,
                now, now, TermsConsentStatus.ACCEPTED, conversationVersionAtPresentation, conversationVersion);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
