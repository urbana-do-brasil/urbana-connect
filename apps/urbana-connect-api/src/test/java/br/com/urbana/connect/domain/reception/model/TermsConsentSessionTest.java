package br.com.urbana.connect.domain.reception.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TermsConsentSessionTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-09-03T12:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(1800);

    @Test
    void followsWebConsentLifecycleAndSeparatesEndReachedFromDecision() {
        TermsConsentSession session = TermsConsentSession.issued(
                "presentation-1", "conversation-1", "contact-1", "destination-1", "unit-1",
                "HML", "DECOR_INTERIORES", "terms-v1", "hash-1", "https://terms.example/v1",
                "Termos de teste", "digest-1", ISSUED_AT, EXPIRES_AT);

        TermsConsentSession presented = session.pagePresented(ISSUED_AT.plusSeconds(1));
        TermsConsentSession reached = presented.endReached(ISSUED_AT.plusSeconds(2));
        TermsConsentSession accepted = reached.accept(ISSUED_AT.plusSeconds(3), "event-accept");

        assertThat(accepted.status()).isEqualTo(TermsConsentSessionStatus.ACCEPTED);
        assertThat(accepted.pagePresentedAt()).isEqualTo(ISSUED_AT.plusSeconds(1));
        assertThat(accepted.endReachedAt()).isEqualTo(ISSUED_AT.plusSeconds(2));
        assertThat(accepted.decidedAt()).isEqualTo(ISSUED_AT.plusSeconds(3));
        assertThat(accepted.decision()).isEqualTo(TermsConsentDecision.ACCEPT);
        assertThat(accepted.accept(ISSUED_AT.plusSeconds(4), "event-replay")).isEqualTo(accepted);
    }

    @Test
    void rejectsDecisionBeforeEndAndOppositeReplay() {
        TermsConsentSession issued = TermsConsentSession.issued(
                "presentation-1", "conversation-1", "contact-1", "destination-1", "unit-1",
                "HML", "DECOR_INTERIORES", "terms-v1", "hash-1", "https://terms.example/v1",
                "Termos de teste", "digest-1", ISSUED_AT, EXPIRES_AT);

        assertThatThrownBy(() -> issued.accept(ISSUED_AT.plusSeconds(1), "event-accept"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("END_REACHED");

        TermsConsentSession declined = issued.pagePresented(ISSUED_AT.plusSeconds(1))
                .endReached(ISSUED_AT.plusSeconds(2))
                .decline(ISSUED_AT.plusSeconds(3), "event-decline");
        assertThatThrownBy(() -> declined.accept(ISSUED_AT.plusSeconds(4), "event-accept"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("opposite");
    }

    @Test
    void expiryDoesNotBecomeDecline() {
        TermsConsentSession session = TermsConsentSession.issued(
                "presentation-1", "conversation-1", "contact-1", "destination-1", "unit-1",
                "HML", "DECOR_INTERIORES", "terms-v1", "hash-1", "https://terms.example/v1",
                "Termos de teste", "digest-1", ISSUED_AT, EXPIRES_AT);

        TermsConsentSession expired = session.expire(EXPIRES_AT);

        assertThat(expired.status()).isEqualTo(TermsConsentSessionStatus.EXPIRED);
        assertThat(expired.decision()).isNull();
    }
}
