package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SpringDataTermsConsentSessionRepository
        extends MongoRepository<TermsConsentSessionDocument, String> {
    Optional<TermsConsentSessionDocument> findByTokenDigest(String tokenDigest);
}
