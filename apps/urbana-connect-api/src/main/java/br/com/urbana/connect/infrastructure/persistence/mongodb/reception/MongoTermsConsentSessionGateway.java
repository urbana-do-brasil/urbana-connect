package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentSession;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentSessionGateway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;

/** Mongo adapter with a revision CAS; the TTL index is attached only to sessions. */
public final class MongoTermsConsentSessionGateway implements TermsConsentSessionGateway {
    private final SpringDataTermsConsentSessionRepository repository;
    private final MongoTemplate template;

    public MongoTermsConsentSessionGateway(SpringDataTermsConsentSessionRepository repository,
                                           MongoTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    @Override
    public TermsConsentSession saveIfAbsent(TermsConsentSession session) {
        Optional<TermsConsentSessionDocument> existing = repository.findById(session.presentationId());
        if (existing.isPresent()) {
            return toDomain(existing.orElseThrow());
        }
        try {
            return toDomain(repository.insert(toDocument(session)));
        } catch (DuplicateKeyException race) {
            return repository.findById(session.presentationId()).map(MongoTermsConsentSessionGateway::toDomain)
                    .orElseThrow(() -> new IllegalStateException("terms session lost during idempotent save", race));
        }
    }

    @Override
    public Optional<TermsConsentSession> findByTokenDigest(String tokenDigest) {
        return repository.findByTokenDigest(tokenDigest).map(MongoTermsConsentSessionGateway::toDomain);
    }

    @Override
    public Optional<TermsConsentSession> findByPresentationId(String presentationId) {
        return repository.findById(presentationId).map(MongoTermsConsentSessionGateway::toDomain);
    }

    @Override
    public TermsConsentSession saveExpected(TermsConsentSession session, long expectedVersion) {
        if (session.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("terms session version does not follow expected version");
        }
        if (template == null) {
            return toDomain(repository.save(toDocument(session)));
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(session.presentationId()),
                Criteria.where("version").is(expectedVersion)));
        TermsConsentSessionDocument updated = template.findAndModify(query, update(session),
                FindAndModifyOptions.options().returnNew(true), TermsConsentSessionDocument.class);
        if (updated != null) {
            return toDomain(updated);
        }
        TermsConsentSession current = findByPresentationId(session.presentationId())
                .orElseThrow(() -> new IllegalStateException("terms session disappeared during transition"));
        if (current.equals(session)) {
            return current;
        }
        throw new IllegalStateException("terms session changed concurrently");
    }

    private static Update update(TermsConsentSession value) {
        return new Update()
                .set("tokenDigest", value.tokenDigest())
                .set("expiresAt", value.expiresAt())
                .set("conversationId", value.conversationId())
                .set("contactId", value.contactId())
                .set("destinationRef", value.destinationRef())
                .set("contractingUnitId", value.contractingUnitId())
                .set("environment", value.environment())
                .set("serviceType", value.serviceType())
                .set("termsVersion", value.termsVersion())
                .set("termsHash", value.termsHash())
                .set("termsResource", value.termsResource())
                .set("termsContent", value.termsContent())
                .set("issuedAt", value.issuedAt())
                .set("status", value.status())
                .set("pagePresentedAt", value.pagePresentedAt())
                .set("endReachedAt", value.endReachedAt())
                .set("decision", value.decision())
                .set("decidedAt", value.decidedAt())
                .set("decisionEventId", value.decisionEventId())
                .set("version", value.version());
    }

    private static TermsConsentSessionDocument toDocument(TermsConsentSession value) {
        TermsConsentSessionDocument document = new TermsConsentSessionDocument();
        document.setPresentationId(value.presentationId());
        document.setTokenDigest(value.tokenDigest());
        document.setExpiresAt(value.expiresAt());
        document.setConversationId(value.conversationId());
        document.setContactId(value.contactId());
        document.setDestinationRef(value.destinationRef());
        document.setContractingUnitId(value.contractingUnitId());
        document.setEnvironment(value.environment());
        document.setServiceType(value.serviceType());
        document.setTermsVersion(value.termsVersion());
        document.setTermsHash(value.termsHash());
        document.setTermsResource(value.termsResource());
        document.setTermsContent(value.termsContent());
        document.setIssuedAt(value.issuedAt());
        document.setStatus(value.status());
        document.setPagePresentedAt(value.pagePresentedAt());
        document.setEndReachedAt(value.endReachedAt());
        document.setDecision(value.decision());
        document.setDecidedAt(value.decidedAt());
        document.setDecisionEventId(value.decisionEventId());
        document.setVersion(value.version());
        return document;
    }

    private static TermsConsentSession toDomain(TermsConsentSessionDocument value) {
        return new TermsConsentSession(value.getPresentationId(), value.getConversationId(), value.getContactId(),
                value.getDestinationRef(), value.getContractingUnitId(), value.getEnvironment(), value.getServiceType(),
                value.getTermsVersion(), value.getTermsHash(), value.getTermsResource(), value.getTermsContent(),
                value.getTokenDigest(), value.getIssuedAt(), value.getExpiresAt(), value.getStatus(),
                value.getPagePresentedAt(), value.getEndReachedAt(), value.getDecision(), value.getDecidedAt(),
                value.getDecisionEventId(), value.getVersion());
    }
}
