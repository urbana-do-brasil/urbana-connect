package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Append-only audit evidence.  There is deliberately no token field: a raw
 * bearer must never become part of legal evidence or a Mongo document.
 */
public record TermsConsentAuditEvent(
        String eventId,
        String presentationId,
        TermsConsentAuditEventType eventType,
        String conversationId,
        String contactId,
        String destinationRef,
        String contractingUnitId,
        String environment,
        String serviceType,
        String termsVersion,
        String termsHash,
        String termsResource,
        Instant occurredAt,
        String decisionEventId) {

    public TermsConsentAuditEvent {
        require(eventId, "eventId");
        require(presentationId, "presentationId");
        eventType = Objects.requireNonNull(eventType, "eventType");
        require(conversationId, "conversationId");
        require(contactId, "contactId");
        require(destinationRef, "destinationRef");
        require(contractingUnitId, "contractingUnitId");
        require(environment, "environment");
        require(serviceType, "serviceType");
        require(termsVersion, "termsVersion");
        require(termsHash, "termsHash");
        require(termsResource, "termsResource");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        if (eventType == TermsConsentAuditEventType.ACCEPTED
                || eventType == TermsConsentAuditEventType.DECLINED) {
            require(decisionEventId, "decisionEventId");
        }
    }

    public static TermsConsentAuditEvent create(String presentationId, TermsConsentAuditEventType eventType,
                                                String conversationId, String contactId, String destinationRef,
                                                String contractingUnitId, String environment, String serviceType,
                                                String termsVersion, String termsHash, String termsResource,
                                                Instant occurredAt, String decisionEventId) {
        String eventId = presentationId + ":" + eventType.name();
        return new TermsConsentAuditEvent(eventId, presentationId, eventType, conversationId, contactId,
                destinationRef, contractingUnitId, environment, serviceType, termsVersion, termsHash,
                termsResource, occurredAt, decisionEventId);
    }

    /**
     * Test/adapter guard kept explicit so a future mapper cannot accidentally
     * add a clear bearer to this immutable model.
     */
    public static TermsConsentAuditEvent withTokenDigest(String presentationId,
                                                         TermsConsentAuditEventType eventType,
                                                         String conversationId, String contactId,
                                                         String destinationRef, String contractingUnitId,
                                                         String environment, String serviceType,
                                                         String termsVersion, String termsHash,
                                                         String termsResource, String tokenDigest,
                                                         Instant occurredAt, String decisionEventId) {
        if (tokenDigest != null && !tokenDigest.isBlank()) {
            throw new IllegalArgumentException("token must not be included in audit events");
        }
        return create(presentationId, eventType, conversationId, contactId, destinationRef,
                contractingUnitId, environment, serviceType, termsVersion, termsHash, termsResource,
                occurredAt, decisionEventId);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
