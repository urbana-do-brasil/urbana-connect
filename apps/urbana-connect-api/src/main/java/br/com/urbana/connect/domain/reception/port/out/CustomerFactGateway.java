package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.CustomerFact;

import java.time.Instant;
import java.util.List;

public interface CustomerFactGateway {
    List<CustomerFact> findCurrentByContactId(String contactId, Instant at);

    List<CustomerFact> findByContactId(String contactId);

    CustomerFact save(CustomerFact fact);
}
