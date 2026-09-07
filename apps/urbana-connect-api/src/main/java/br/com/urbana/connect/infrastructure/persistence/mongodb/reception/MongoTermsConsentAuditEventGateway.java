package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentAuditEvent;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditEventGateway;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

/** Append-only event adapter. Existing event IDs are never updated. */
public final class MongoTermsConsentAuditEventGateway implements TermsConsentAuditEventGateway {
    private final SpringDataTermsConsentAuditEventRepository repository;

    public MongoTermsConsentAuditEventGateway(SpringDataTermsConsentAuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public TermsConsentAuditEvent appendIfAbsent(TermsConsentAuditEvent event) {
        return repository.findById(event.eventId()).map(MongoTermsConsentAuditEventGateway::toDomain)
                .orElseGet(() -> insert(event));
    }

    @Override
    public List<TermsConsentAuditEvent> findByPresentationId(String presentationId) {
        return repository.findByPresentationIdOrderByOccurredAtAscEventIdAsc(presentationId)
                .stream().map(MongoTermsConsentAuditEventGateway::toDomain).toList();
    }

    private TermsConsentAuditEvent insert(TermsConsentAuditEvent event) {
        try {
            return toDomain(repository.insert(toDocument(event)));
        } catch (DuplicateKeyException race) {
            return repository.findById(event.eventId()).map(MongoTermsConsentAuditEventGateway::toDomain)
                    .orElseThrow(() -> new IllegalStateException("terms event lost during idempotent append", race));
        }
    }

    private static TermsConsentAuditEventDocument toDocument(TermsConsentAuditEvent value) {
        TermsConsentAuditEventDocument document = new TermsConsentAuditEventDocument();
        document.setEventId(value.eventId()); document.setPresentationId(value.presentationId());
        document.setEventType(value.eventType()); document.setConversationId(value.conversationId());
        document.setContactId(value.contactId()); document.setDestinationRef(value.destinationRef());
        document.setContractingUnitId(value.contractingUnitId()); document.setEnvironment(value.environment());
        document.setServiceType(value.serviceType()); document.setTermsVersion(value.termsVersion());
        document.setTermsHash(value.termsHash()); document.setTermsResource(value.termsResource());
        document.setOccurredAt(value.occurredAt()); document.setDecisionEventId(value.decisionEventId());
        return document;
    }

    private static TermsConsentAuditEvent toDomain(TermsConsentAuditEventDocument value) {
        return new TermsConsentAuditEvent(value.getEventId(), value.getPresentationId(), value.getEventType(),
                value.getConversationId(), value.getContactId(), value.getDestinationRef(),
                value.getContractingUnitId(), value.getEnvironment(), value.getServiceType(),
                value.getTermsVersion(), value.getTermsHash(), value.getTermsResource(), value.getOccurredAt(),
                value.getDecisionEventId());
    }
}
