package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface SpringDataCustomerFactRepository extends MongoRepository<CustomerFactDocument, String> {
    List<CustomerFactDocument> findByContactId(String contactId);
    List<CustomerFactDocument> findByContactIdAndValidFromLessThanEqualAndSupersededByIsNull(String contactId, Instant at);
}
