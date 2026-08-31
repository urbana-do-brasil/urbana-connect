package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SpringDataTermsConsentAuditRepository extends MongoRepository<TermsConsentAuditDocument, String> {
    Optional<TermsConsentAuditDocument> findFirstByConversationIdAndContractingUnitIdAndStatusOrderByPresentedAtDesc(
            String conversationId, String contractingUnitId, TermsConsentStatus status);
}
