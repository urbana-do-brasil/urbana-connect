package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.List;

public interface SpringDataDomainToolInvocationRepository extends MongoRepository<DomainToolInvocationDocument, String> {
    Optional<DomainToolInvocationDocument> findByIdempotencyKey(String idempotencyKey);
    List<DomainToolInvocationDocument> findByTurnId(String turnId);
    List<DomainToolInvocationDocument> findByContactIdOrderByCreatedAtAsc(String contactId);
}
