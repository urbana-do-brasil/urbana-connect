package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringDataTermsConsentAuditEventRepository
        extends MongoRepository<TermsConsentAuditEventDocument, String> {
    List<TermsConsentAuditEventDocument> findByPresentationIdOrderByOccurredAtAscEventIdAsc(String presentationId);
}
