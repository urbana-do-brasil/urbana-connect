package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versioned customer fact; correction supersedes rather than deletes history. */
public record CustomerFact(
        String id,
        String contactId,
        String type,
        String value,
        FactConfidence confidence,
        String sourceMessageId,
        Instant validFrom,
        Instant validUntil,
        String supersededBy) {

    public CustomerFact {
        id = require(id, "id");
        contactId = require(contactId, "contactId");
        type = require(type, "type").toUpperCase();
        value = require(value, "value");
        confidence = Objects.requireNonNull(confidence, "confidence");
        sourceMessageId = require(sourceMessageId, "sourceMessageId");
        validFrom = Objects.requireNonNull(validFrom, "validFrom");
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("validUntil cannot precede validFrom");
        }
    }

    public CustomerFact(String contactId, String type, String value, FactConfidence confidence,
                        String sourceMessageId, Instant validFrom) {
        this(UUID.randomUUID().toString(), contactId, type, value, confidence, sourceMessageId,
                validFrom, null, null);
    }

    public static CustomerFact confirmed(String contactId, String type, String value,
                                         String sourceMessageId, Instant validFrom) {
        return new CustomerFact(contactId, type, value, FactConfidence.CONFIRMED, sourceMessageId, validFrom);
    }

    public static CustomerFact tentative(String contactId, String type, String value,
                                         String sourceMessageId, Instant validFrom) {
        return new CustomerFact(contactId, type, value, FactConfidence.TENTATIVE, sourceMessageId, validFrom);
    }

    public boolean isCurrentAt(Instant instant) {
        return !validFrom.isAfter(instant) && (validUntil == null || instant.isBefore(validUntil));
    }

    public boolean isConfirmedCurrentAt(Instant instant) {
        return confidence == FactConfidence.CONFIRMED && supersededBy == null && isCurrentAt(instant);
    }

    public CustomerFact supersede(String replacementId, Instant until) {
        require(replacementId, "replacementId");
        Objects.requireNonNull(until, "until");
        if (until.isBefore(validFrom)) {
            throw new IllegalArgumentException("supersede time cannot precede validFrom");
        }
        return new CustomerFact(id, contactId, type, value, confidence, sourceMessageId,
                validFrom, until, replacementId);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
