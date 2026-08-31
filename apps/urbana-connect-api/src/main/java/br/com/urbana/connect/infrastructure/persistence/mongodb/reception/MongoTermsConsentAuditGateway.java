package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentAudit;
import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditGateway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;

public final class MongoTermsConsentAuditGateway implements TermsConsentAuditGateway {
    private final SpringDataTermsConsentAuditRepository repository;
    private final MongoTemplate template;

    public MongoTermsConsentAuditGateway(SpringDataTermsConsentAuditRepository repository, MongoTemplate template) {
        this.repository = repository; this.template = template;
    }
    public Optional<TermsConsentAudit> findByPresentationId(String id) { return repository.findById(id).map(this::toDomain); }
    public Optional<TermsConsentAudit> findPresented(String conversationId, String unitId) {
        return repository.findFirstByConversationIdAndContractingUnitIdAndStatusOrderByPresentedAtDesc(
                conversationId, unitId, TermsConsentStatus.PRESENTED).map(this::toDomain);
    }
    public TermsConsentAudit savePresentationIfAbsent(TermsConsentAudit audit) {
        if (audit == null || audit.status() != TermsConsentStatus.PRESENTED) {
            throw new IllegalArgumentException("only a presented audit can be persisted");
        }
        Optional<TermsConsentAuditDocument> existing = repository.findById(audit.presentationId());
        if (existing.isPresent()) {
            return toDomain(existing.orElseThrow());
        }
        try { return toDomain(repository.insert(toDocument(audit))); }
        catch (DuplicateKeyException duplicate) { return findByPresentationId(audit.presentationId()).orElseThrow(() -> duplicate); }
    }
    public TermsConsentAudit acceptIfPresented(String id, String eventId, String messageId, String text,
                                                long version, Instant now) {
        require(id, "presentationId");
        require(eventId, "acceptanceEventId");
        require(messageId, "acceptanceMessageId");
        require(text, "acceptanceTextExact");
        if (version < 0) throw new IllegalArgumentException("conversationVersion must be non-negative");
        if (now == null) throw new IllegalArgumentException("acceptedAt must not be null");
        Query query = Query.query(new Criteria().andOperator(Criteria.where("_id").is(id),
                Criteria.where("status").is(TermsConsentStatus.PRESENTED),
                Criteria.where("presentedAt").lte(now)));
        Update update = new Update().set("status", TermsConsentStatus.ACCEPTED)
                .set("acceptanceEventId", eventId).set("acceptanceMessageId", messageId)
                .set("acceptanceTextExact", text).set("acceptedAt", now).set("recordedAt", now)
                .set("conversationVersionAtAcceptance", version);
        TermsConsentAuditDocument changed = template.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), TermsConsentAuditDocument.class);
        if (changed != null) return toDomain(changed);
        TermsConsentAudit existing = findByPresentationId(id)
                .orElseThrow(() -> new IllegalStateException("terms presentation evidence is missing"));
        if (existing.status() != TermsConsentStatus.ACCEPTED) throw new IllegalStateException("terms acceptance was not recorded");
        return existing;
    }
    private TermsConsentAuditDocument toDocument(TermsConsentAudit a) {
        TermsConsentAuditDocument d = new TermsConsentAuditDocument(); d.setPresentationId(a.presentationId());
        d.setConversationId(a.conversationId()); d.setContactId(a.contactId()); d.setTurnId(a.turnId()); d.setContractingUnitId(a.contractingUnitId());
        d.setEnvironmentLabelSnapshot(a.environmentLabelSnapshot()); d.setEnvironmentSourceMessageId(a.environmentSourceMessageId());
        d.setServiceType(a.serviceType()); d.setTermsResource(a.termsResource()); d.setTermsVersion(a.termsVersion());
        d.setPrepareTermsInvocationId(a.prepareTermsInvocationId()); d.setTermsOutboundMessageId(a.termsOutboundMessageId());
        d.setPresentedAt(a.presentedAt()); d.setAcceptanceMessageId(a.acceptanceMessageId()); d.setAcceptanceEventId(a.acceptanceEventId());
        d.setAcceptanceTextExact(a.acceptanceTextExact()); d.setAcceptedAt(a.acceptedAt()); d.setRecordedAt(a.recordedAt()); d.setStatus(a.status());
        d.setConversationVersionAtPresentation(a.conversationVersionAtPresentation()); d.setConversationVersionAtAcceptance(a.conversationVersionAtAcceptance()); return d;
    }
    private TermsConsentAudit toDomain(TermsConsentAuditDocument d) { return new TermsConsentAudit(d.getPresentationId(), d.getConversationId(), d.getContactId(), d.getTurnId(), d.getContractingUnitId(), d.getEnvironmentLabelSnapshot(), d.getEnvironmentSourceMessageId(), d.getServiceType(), d.getTermsResource(), d.getTermsVersion(), d.getPrepareTermsInvocationId(), d.getTermsOutboundMessageId(), d.getPresentedAt(), d.getAcceptanceMessageId(), d.getAcceptanceEventId(), d.getAcceptanceTextExact(), d.getAcceptedAt(), d.getRecordedAt(), d.getStatus(), d.getConversationVersionAtPresentation(), d.getConversationVersionAtAcceptance()); }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
