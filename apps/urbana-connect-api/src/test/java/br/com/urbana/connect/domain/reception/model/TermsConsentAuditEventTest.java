package br.com.urbana.connect.domain.reception.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TermsConsentAuditEventTest {
    @Test
    void isImmutableAndContainsTheCompleteBindingWithoutTheBearerToken() {
        TermsConsentAuditEvent event = TermsConsentAuditEvent.create(
                "presentation-1", TermsConsentAuditEventType.ACCEPTED, "conversation-1", "contact-1",
                "destination-1", "unit-1", "HML", "DECOR_INTERIORES", "terms-v1", "hash-1",
                "https://terms.example/v1", Instant.parse("2026-09-03T12:00:00Z"), "decision-1");

        assertThat(event.eventId()).isEqualTo("presentation-1:ACCEPTED");
        assertThat(event.eventType()).isEqualTo(TermsConsentAuditEventType.ACCEPTED);
    }

    @Test
    void rejectsBlankBindingAndTokenMaterial() {
        assertThatThrownBy(() -> TermsConsentAuditEvent.create(
                "", TermsConsentAuditEventType.ISSUED, "conversation-1", "contact-1",
                "destination-1", "unit-1", "HML", "DECOR_INTERIORES", "terms-v1", "hash-1",
                "https://terms.example/v1", Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TermsConsentAuditEvent.withTokenDigest(
                "presentation-1", TermsConsentAuditEventType.ISSUED, "conversation-1", "contact-1",
                "destination-1", "unit-1", "HML", "DECOR_INTERIORES", "terms-v1", "hash-1",
                "https://terms.example/v1", "raw-token", Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }
}
