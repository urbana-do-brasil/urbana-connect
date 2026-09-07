package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.TermsConsentSession;

import java.util.Optional;

/** Persistence port for the short-lived terms session projection. */
public interface TermsConsentSessionGateway {
    TermsConsentSession saveIfAbsent(TermsConsentSession session);

    Optional<TermsConsentSession> findByTokenDigest(String tokenDigest);

    Optional<TermsConsentSession> findByPresentationId(String presentationId);

    /** CAS hook used for concurrent browser callbacks. */
    default TermsConsentSession saveExpected(TermsConsentSession session, long expectedVersion) {
        return saveIfAbsent(session);
    }
}
