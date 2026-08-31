package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.TermsConsentAudit;

import java.util.Optional;

public interface TermsConsentAuditGateway {
    Optional<TermsConsentAudit> findByPresentationId(String presentationId);
    Optional<TermsConsentAudit> findPresented(String conversationId, String contractingUnitId);
    TermsConsentAudit savePresentationIfAbsent(TermsConsentAudit audit);
    TermsConsentAudit acceptIfPresented(String presentationId, String acceptanceEventId,
                                        String acceptanceMessageId, String acceptanceTextExact,
                                        long conversationVersion, java.time.Instant acceptedAt);
}
