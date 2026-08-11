package br.com.urbana.connect.domain.reception.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CustomerFactTest {

    private final Instant now = Instant.parse("2026-08-04T12:00:00Z");

    @Test
    void keepsSourceAndConfirmedVersion() {
        CustomerFact fact = CustomerFact.confirmed("contact-1", "occupation", "arquiteta", "message-1", now);

        assertThat(fact.type()).isEqualTo("OCCUPATION");
        assertThat(fact.confidence()).isEqualTo(FactConfidence.CONFIRMED);
        assertThat(fact.sourceMessageId()).isEqualTo("message-1");
        assertThat(fact.isConfirmedCurrentAt(now.plusSeconds(1))).isTrue();
    }

    @Test
    void correctionSupersedesPreviousFactWithoutDeletingItsProvenance() {
        CustomerFact original = CustomerFact.confirmed("contact-1", "occupation", "arquiteta", "message-1", now);
        CustomerFact corrected = CustomerFact.confirmed("contact-1", "occupation", "designer", "message-2", now.plusSeconds(10));
        CustomerFact superseded = original.supersede(corrected.id(), now.plusSeconds(10));

        assertThat(superseded.supersededBy()).isEqualTo(corrected.id());
        assertThat(superseded.sourceMessageId()).isEqualTo("message-1");
        assertThat(superseded.isConfirmedCurrentAt(now.plusSeconds(11))).isFalse();
        assertThat(corrected.isConfirmedCurrentAt(now.plusSeconds(11))).isTrue();
    }

    @Test
    void requiresAValidProvenanceAndTemporalVersion() {
        assertThatIllegalArgumentException().isThrownBy(() -> CustomerFact.confirmed(
                "contact-1", "occupation", "arquiteta", "", now));
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomerFact(
                "id", "contact-1", "occupation", "arquiteta", FactConfidence.CONFIRMED,
                "message-1", now, now.minusSeconds(1), null));
    }
}
