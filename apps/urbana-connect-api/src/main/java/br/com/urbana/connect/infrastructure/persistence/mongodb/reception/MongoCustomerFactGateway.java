package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;

import java.time.Instant;
import java.util.List;

public class MongoCustomerFactGateway implements CustomerFactGateway {
    private final SpringDataCustomerFactRepository repository;

    public MongoCustomerFactGateway(SpringDataCustomerFactRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CustomerFact> findCurrentByContactId(String contactId, Instant at) {
        return repository.findByContactIdAndValidFromLessThanEqualAndSupersededByIsNull(contactId, at).stream()
                .filter(document -> document.getValidUntil() == null || at.isBefore(document.getValidUntil()))
                .map(this::toDomain).toList();
    }

    @Override
    public List<CustomerFact> findByContactId(String contactId) {
        return repository.findByContactId(contactId).stream().map(this::toDomain).toList();
    }

    @Override
    public CustomerFact save(CustomerFact fact) {
        return toDomain(repository.save(toDocument(fact)));
    }

    private CustomerFactDocument toDocument(CustomerFact value) {
        CustomerFactDocument d = new CustomerFactDocument();
        d.setId(value.id()); d.setContactId(value.contactId()); d.setType(value.type()); d.setValue(value.value());
        d.setConfidence(value.confidence()); d.setSourceMessageId(value.sourceMessageId()); d.setValidFrom(value.validFrom());
        d.setValidUntil(value.validUntil()); d.setSupersededBy(value.supersededBy());
        return d;
    }

    private CustomerFact toDomain(CustomerFactDocument d) {
        return new CustomerFact(d.getId(), d.getContactId(), d.getType(), d.getValue(), d.getConfidence(),
                d.getSourceMessageId(), d.getValidFrom(), d.getValidUntil(), d.getSupersededBy());
    }
}
