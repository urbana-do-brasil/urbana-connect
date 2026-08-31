package br.com.urbana.connect.domain.reception.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TermsConsentAuditTest {
    private static final Instant PRESENTED_AT = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    void requiresCompletePresentationEvidenceAndNonNegativeVersion() {
        TermsConsentAudit valid = presented();
        assertThat(valid.status()).isEqualTo(TermsConsentStatus.PRESENTED);

        assertThatThrownBy(() -> new TermsConsentAudit(null, "conversation", "contact", "turn", "unit",
                "sala", "message-environment", "DECOR", "terms", null, "invocation", "outbound",
                PRESENTED_AT, null, null, null, null, PRESENTED_AT, TermsConsentStatus.PRESENTED, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TermsConsentAudit("presentation", "conversation", "contact", "turn", "unit",
                "sala", "message-environment", "DECOR", "terms", null, "invocation", "outbound",
                PRESENTED_AT, null, null, null, null, PRESENTED_AT, TermsConsentStatus.PRESENTED, -1, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }

    @Test
    void rejectsAcceptanceEvidenceOnPresentedAuditAndIncompleteAcceptedAudit() {
        assertThatThrownBy(() -> new TermsConsentAudit("presentation", "conversation", "contact", "turn", "unit",
                "sala", "message-environment", "DECOR", "terms", null, "invocation", "outbound",
                PRESENTED_AT, "message", null, null, null, PRESENTED_AT, TermsConsentStatus.PRESENTED, 0, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("presented audit");

        assertThatThrownBy(() -> new TermsConsentAudit("presentation", "conversation", "contact", "turn", "unit",
                "sala", "message-environment", "DECOR", "terms", null, "invocation", "outbound",
                PRESENTED_AT, null, null, null, null, PRESENTED_AT, TermsConsentStatus.ACCEPTED, 0, 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("acceptanceMessageId");
        assertThatThrownBy(() -> new TermsConsentAudit("presentation", "conversation", "contact", "turn", "unit",
                "sala", "message-environment", "DECOR", "terms", null, "invocation", "outbound",
                PRESENTED_AT, "message", "event", "Aceito", PRESENTED_AT, PRESENTED_AT,
                TermsConsentStatus.ACCEPTED, 0, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("acceptance version");
    }

    @Test
    void acceptsOnlyAfterPresentationAndKeepsTheFirstAcceptanceOnReplay() {
        TermsConsentAudit presented = presented();
        TermsConsentAudit accepted = presented.accept("message-accept", "event-accept", "Aceito",
                PRESENTED_AT.plusSeconds(1), 2);
        TermsConsentAudit replay = accepted.accept("message-replay", "event-replay", "Outro texto",
                PRESENTED_AT.plusSeconds(2), 3);

        assertThat(accepted.status()).isEqualTo(TermsConsentStatus.ACCEPTED);
        assertThat(accepted.acceptanceMessageId()).isEqualTo("message-accept");
        assertThat(accepted.acceptanceEventId()).isEqualTo("event-accept");
        assertThat(accepted.acceptanceTextExact()).isEqualTo("Aceito");
        assertThat(accepted.conversationVersionAtAcceptance()).isEqualTo(2L);
        assertThat(replay).isSameAs(accepted);
    }

    @Test
    void rejectsBlankOrPrematureAcceptanceData() {
        TermsConsentAudit presented = presented();
        assertThatThrownBy(() -> presented.accept("", "event", "Aceito", PRESENTED_AT.plusSeconds(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> presented.accept("message", " ", "Aceito", PRESENTED_AT.plusSeconds(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> presented.accept("message", "event", " ", PRESENTED_AT.plusSeconds(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> presented.accept("message", "event", "Aceito", PRESENTED_AT.minusSeconds(1), 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("precede");
        assertThatThrownBy(() -> presented.accept("message", "event", "Aceito", PRESENTED_AT.plusSeconds(1), -1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> presented.accept("message", "event", "Aceito", null, 1))
                .isInstanceOf(NullPointerException.class);
    }

    private static TermsConsentAudit presented() {
        return new TermsConsentAudit("presentation-1", "conversation-1", "contact-1", "turn-1", "unit-1",
                "sala", "message-environment", "DECOR_INTERIORES", "https://example.test/terms", "v1",
                "invocation-1", "outbound-1", PRESENTED_AT, null, null, null, null, PRESENTED_AT,
                TermsConsentStatus.PRESENTED, 1, null);
    }
}
