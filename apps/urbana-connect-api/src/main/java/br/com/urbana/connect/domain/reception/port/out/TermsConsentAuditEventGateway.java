package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.TermsConsentAuditEvent;

import java.util.List;

/** Append-only persistence port for terms evidence. */
public interface TermsConsentAuditEventGateway {
    TermsConsentAuditEvent appendIfAbsent(TermsConsentAuditEvent event);

    List<TermsConsentAuditEvent> findByPresentationId(String presentationId);
}
