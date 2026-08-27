package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.CustomerFactType;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the contact-scoped memory used when a customer returns.
 *
 * <p>The gateway query is scoped by contact, and the application boundary
 * applies the same scope again before projecting anything. This makes the
 * projection safe even when an adapter accidentally returns a broader
 * result. Only confirmed, non-superseded facts that are valid at the
 * projection instant are reusable; uncertain or stale observations remain
 * eligible for collection through {@link Projection#missingIcpFields()}.</p>
 */
public final class ReturningCustomerService {
    private final CustomerFactGateway facts;
    private final CommercialPolicyService policy;
    private final Clock clock;

    public ReturningCustomerService(CustomerFactGateway facts) {
        this(facts, new CommercialPolicyService(), Clock.systemUTC());
    }

    public ReturningCustomerService(CustomerFactGateway facts, CommercialPolicyService policy) {
        this(facts, policy, Clock.systemUTC());
    }

    public ReturningCustomerService(CustomerFactGateway facts, CommercialPolicyService policy, Clock clock) {
        this.facts = Objects.requireNonNull(facts, "facts");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Projection project(String contactId) {
        return project(contactId, clock.instant());
    }

    public Projection project(String contactId, Instant at) {
        String scopedContactId = require(contactId, "contactId");
        Instant projectionTime = Objects.requireNonNull(at, "at");
        List<CustomerFact> confirmedCurrent = confirmedCurrentFacts(scopedContactId, projectionTime);

        String selectedService = confirmedCurrent.stream()
                .filter(fact -> CustomerFactType.SELECTED_SERVICE.name().equals(fact.type()))
                .map(CustomerFact::value)
                .findFirst()
                .orElse(null);
        List<String> previousServices = confirmedCurrent.stream()
                .filter(fact -> CustomerFactType.SELECTED_SERVICE.name().equals(fact.type()))
                .map(CustomerFact::value)
                .toList();

        return new Projection(scopedContactId, confirmedCurrent, selectedService,
                policy.missingIcpFields(confirmedCurrent, projectionTime), previousServices);
    }

    private List<CustomerFact> confirmedCurrentFacts(String contactId, Instant at) {
        List<CustomerFact> queried = facts.findCurrentByContactId(contactId, at);
        if (queried == null || queried.isEmpty()) {
            return List.of();
        }

        Map<String, CustomerFact> latestByType = new LinkedHashMap<>();
        for (CustomerFact fact : queried) {
            if (fact == null || !contactId.equals(fact.contactId()) || fact.supersededBy() != null
                    || fact.validFrom().isAfter(at)) {
                continue;
            }
            CustomerFact previous = latestByType.get(fact.type());
            if (previous == null || CURRENT_FACT_ORDER.compare(fact, previous) > 0) {
                latestByType.put(fact.type(), fact);
            }
        }
        return latestByType.values().stream()
                .filter(fact -> fact.isReusableAt(at))
                .toList();
    }

    private static final Comparator<CustomerFact> CURRENT_FACT_ORDER = Comparator
            .comparing(CustomerFact::validFrom)
            .thenComparing(CustomerFact::id);

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public record Projection(String contactId,
                             List<CustomerFact> facts,
                             String selectedService,
                             List<String> missingIcpFields,
                             List<String> previousServices) {
        public Projection {
            contactId = require(contactId, "contactId");
            facts = facts == null ? List.of() : List.copyOf(facts);
            missingIcpFields = missingIcpFields == null ? List.of() : List.copyOf(missingIcpFields);
            previousServices = previousServices == null ? List.of() : List.copyOf(previousServices);
            if (selectedService != null && selectedService.isBlank()) {
                throw new IllegalArgumentException("selectedService must not be blank");
            }
        }

        public boolean hasCompleteIcp() {
            return missingIcpFields.isEmpty();
        }
    }
}
