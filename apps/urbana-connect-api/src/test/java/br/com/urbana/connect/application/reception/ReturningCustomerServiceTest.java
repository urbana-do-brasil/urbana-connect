package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReturningCustomerServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void reusesConfirmedCurrentFactsAndSelectedServiceWithoutRepeatingCompleteIcp() {
        CustomerFact pronoun = CustomerFact.confirmed(
                "contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "message-pronoun", NOW.minusSeconds(30));
        CustomerFact firstHiring = CustomerFact.confirmed(
                "contact-1", "FIRST_TIME_HIRING", "YES", "message-first-hiring", NOW.minusSeconds(20));
        CustomerFact occupation = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "DESIGNER", "message-occupation", NOW.minusSeconds(10));
        CustomerFact selectedService = CustomerFact.confirmed(
                "contact-1", "SELECTED_SERVICE", "DECOR", "message-service", NOW.minusSeconds(5));
        MemoryFacts gateway = new MemoryFacts(List.of(
                pronoun, firstHiring, occupation, selectedService,
                CustomerFact.tentative("contact-1", "NEED", "sala", "message-need", NOW)));

        ReturningCustomerService.Projection projection = service(gateway).project("contact-1", NOW);

        assertThat(gateway.requestedContactIds).containsExactly("contact-1");
        assertThat(projection.contactId()).isEqualTo("contact-1");
        assertThat(projection.facts()).containsExactlyInAnyOrder(
                pronoun, firstHiring, occupation, selectedService);
        assertThat(projection.facts()).allMatch(fact -> fact.confidence()
                == br.com.urbana.connect.domain.reception.model.FactConfidence.CONFIRMED);
        assertThat(projection.selectedService()).isEqualTo("DECOR");
        assertThat(projection.previousServices()).containsExactly("DECOR");
        assertThat(projection.missingIcpFields()).isEmpty();
        assertThat(projection.facts()).extracting(CustomerFact::sourceMessageId)
                .containsExactlyInAnyOrder(
                        "message-pronoun", "message-first-hiring", "message-occupation", "message-service");
    }

    @Test
    void asksOnlyForIcpFieldsThatAreTentativeStaleOrAbsentAndAcceptsPreferNotToAnswer() {
        CustomerFact validPreference = CustomerFact.confirmed(
                "contact-1", "PRONOUN_PREFERENCE", "PREFER_NOT_TO_ANSWER", "message-preference", NOW);
        CustomerFact tentativeHiring = CustomerFact.tentative(
                "contact-1", "FIRST_TIME_HIRING", "YES", "message-tentative", NOW.minusSeconds(20));
        CustomerFact staleOccupation = new CustomerFact(
                "occupation-old", "contact-1", "OCCUPATION", "ARQUITETA",
                br.com.urbana.connect.domain.reception.model.FactConfidence.CONFIRMED,
                "message-stale", NOW.minusSeconds(30), NOW.minusSeconds(1), null);
        CustomerFact service = CustomerFact.confirmed(
                "contact-1", "SELECTED_SERVICE", "DECOR_REFORMA", "message-service", NOW);
        MemoryFacts gateway = new MemoryFacts(List.of(validPreference, tentativeHiring, staleOccupation, service));

        ReturningCustomerService.Projection projection = service(gateway).project("contact-1", NOW);

        assertThat(projection.facts()).containsExactlyInAnyOrder(validPreference, service);
        assertThat(projection.selectedService()).isEqualTo("DECOR_REFORMA");
        assertThat(projection.missingIcpFields())
                .containsExactly("FIRST_TIME_HIRING", "OCCUPATION");
        assertThat(projection.missingIcpFields())
                .doesNotContain("PRONOUN_PREFERENCE", "SELECTED_SERVICE");
    }

    @Test
    void keepsCorrectedFactHistoryAndNeverExposesTheSentinelContact() {
        CustomerFact originalOccupation = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "ARQUITETA", "message-original", NOW.minusSeconds(30));
        CustomerFact correctedOccupation = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "DESIGNER", "message-correction", NOW.minusSeconds(10));
        CustomerFact supersededOriginal = originalOccupation.supersede(
                correctedOccupation.id(), correctedOccupation.validFrom());
        CustomerFact contactOnePreference = CustomerFact.confirmed(
                "contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "message-contact-one", NOW);
        CustomerFact sentinelOccupation = CustomerFact.confirmed(
                "sentinel-contact", "OCCUPATION", "ENGENHEIRO", "message-sentinel", NOW);
        CustomerFact sentinelService = CustomerFact.confirmed(
                "sentinel-contact", "SELECTED_SERVICE", "DECOR_FACHADA", "message-sentinel-service", NOW);
        MemoryFacts gateway = new MemoryFacts(List.of(
                supersededOriginal, correctedOccupation, contactOnePreference,
                sentinelOccupation, sentinelService));
        ReturningCustomerService returning = service(gateway);

        ReturningCustomerService.Projection contactOne = returning.project("contact-1", NOW);
        ReturningCustomerService.Projection sentinel = returning.project("sentinel-contact", NOW);

        assertThat(contactOne.facts()).contains(correctedOccupation, contactOnePreference)
                .doesNotContain(supersededOriginal, sentinelOccupation, sentinelService);
        assertThat(contactOne.selectedService()).isNull();
        assertThat(contactOne.missingIcpFields())
                .containsExactly("FIRST_TIME_HIRING");
        assertThat(sentinel.facts()).containsExactlyInAnyOrder(sentinelOccupation, sentinelService)
                .doesNotContain(correctedOccupation, contactOnePreference);
        assertThat(sentinel.selectedService()).isEqualTo("DECOR_FACHADA");
        assertThat(sentinel.missingIcpFields())
                .containsExactly("PRONOUN_PREFERENCE", "FIRST_TIME_HIRING");

        assertThat(gateway.allFacts).contains(supersededOriginal)
                .extracting(CustomerFact::sourceMessageId)
                .contains("message-original");
    }

    private static ReturningCustomerService service(MemoryFacts gateway) {
        return new ReturningCustomerService(
                gateway,
                new CommercialPolicyService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class MemoryFacts implements CustomerFactGateway {
        private final List<CustomerFact> allFacts;
        private final List<String> requestedContactIds = new ArrayList<>();

        private MemoryFacts(List<CustomerFact> allFacts) {
            this.allFacts = new ArrayList<>(allFacts);
        }

        @Override
        public List<CustomerFact> findCurrentByContactId(String contactId, Instant at) {
            requestedContactIds.add(contactId);
            // Deliberately return the shared store: the application boundary
            // must remain safe even if an adapter is too broad.
            return List.copyOf(allFacts);
        }

        @Override
        public List<CustomerFact> findByContactId(String contactId) {
            throw new AssertionError("returning projection must use the scoped current query");
        }

        @Override
        public CustomerFact save(CustomerFact fact) {
            allFacts.add(fact);
            return fact;
        }
    }
}
