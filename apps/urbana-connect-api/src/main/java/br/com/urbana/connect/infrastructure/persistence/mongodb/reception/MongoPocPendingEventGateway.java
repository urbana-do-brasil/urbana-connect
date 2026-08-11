package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.PocPendingEvent;
import br.com.urbana.connect.domain.reception.model.PocPendingEventStatus;
import br.com.urbana.connect.domain.reception.port.out.PocPendingEventGateway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Mongo-backed queue with a conditional claim token. */
public final class MongoPocPendingEventGateway implements PocPendingEventGateway {
    private static final Comparator<PocPendingEvent> PENDING_ORDER = Comparator
            .comparing(PocPendingEvent::contactId)
            .thenComparing(PocPendingEvent::occurredAt)
            .thenComparing(PocPendingEvent::acceptedAt)
            .thenComparing(PocPendingEvent::eventId);

    private final SpringDataPocPendingEventRepository repository;
    private final MongoTemplate template;

    public MongoPocPendingEventGateway(SpringDataPocPendingEventRepository repository, MongoTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    @Override
    public PocPendingEvent saveIfAbsent(PocPendingEvent event) {
        Optional<PocPendingEventDocument> existing = repository.findById(event.eventId());
        if (existing.isPresent()) return toDomain(existing.orElseThrow());
        try {
            return toDomain(repository.save(toDocument(event)));
        } catch (DuplicateKeyException race) {
            return repository.findById(event.eventId()).map(this::toDomain)
                    .orElseThrow(() -> new IllegalStateException("pending event lost during idempotent save", race));
        }
    }

    @Override
    public Optional<PocPendingEvent> findByEventId(String eventId) {
        return repository.findById(eventId).map(this::toDomain);
    }

    @Override
    public Optional<PocPendingEvent> claim(String eventId, String claimToken, Instant now, Duration leaseTtl) {
        if (template == null) return claimRepositoryFallback(eventId, claimToken, now, leaseTtl);
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(eventId),
                new Criteria().orOperator(
                        Criteria.where("status").is(PocPendingEventStatus.QUEUED),
                        new Criteria().andOperator(
                                Criteria.where("status").is(PocPendingEventStatus.CLAIMED),
                                Criteria.where("claimedAt").lte(now.minus(leaseTtl))))));
        PocPendingEventDocument claimed = template.findAndModify(query,
                new Update().set("status", PocPendingEventStatus.CLAIMED)
                        .set("claimToken", claimToken).set("claimedAt", now),
                FindAndModifyOptions.options().returnNew(true), PocPendingEventDocument.class);
        return claimed == null ? Optional.empty() : Optional.of(toDomain(claimed));
    }

    @Override
    public List<PocPendingEvent> findRecoverable(Instant now, Duration leaseTtl) {
        List<PocPendingEventDocument> documents = repository
                .findByStatusInOrderByContactIdAscOccurredAtAscAcceptedAtAscEventIdAsc(
                        List.of(PocPendingEventStatus.QUEUED, PocPendingEventStatus.CLAIMED));
        if (documents == null || documents.isEmpty()) {
            documents = repository.findByStatusIn(List.of(PocPendingEventStatus.QUEUED, PocPendingEventStatus.CLAIMED));
        }
        if (documents == null) return List.of();
        return documents
                .stream().map(this::toDomain).filter(event -> event.claimableAt(now, leaseTtl))
                .sorted(PENDING_ORDER).toList();
    }

    @Override
    public List<PocPendingEvent> findByContactId(String contactId) {
        List<PocPendingEventDocument> documents = repository
                .findByContactIdOrderByOccurredAtAscAcceptedAtAscEventIdAsc(contactId);
        if (documents == null || documents.isEmpty()) {
            documents = repository.findByContactId(contactId);
        }
        if (documents == null) return List.of();
        return documents.stream().map(this::toDomain).sorted(PENDING_ORDER).toList();
    }

    @Override
    public Optional<PocPendingEvent> complete(String eventId, String claimToken, Instant now) {
        if (template == null) {
            return repository.findById(eventId).map(this::toDomain)
                    .filter(event -> event.status() == PocPendingEventStatus.CLAIMED
                            && claimToken.equals(event.claimToken()))
                    .map(event -> repository.save(toDocument(event.complete(now))))
                    .map(this::toDomain);
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(eventId), Criteria.where("status").is(PocPendingEventStatus.CLAIMED),
                Criteria.where("claimToken").is(claimToken)));
        PocPendingEventDocument completed = template.findAndModify(query,
                new Update().set("status", PocPendingEventStatus.COMPLETED).set("completedAt", now),
                FindAndModifyOptions.options().returnNew(true), PocPendingEventDocument.class);
        return completed == null ? Optional.empty() : Optional.of(toDomain(completed));
    }

    @Override
    public Optional<PocPendingEvent> requeueIfRetrySafe(String eventId, String claimToken, Instant now) {
        if (template == null) {
            return requeueRepositoryFallback(eventId, claimToken, now);
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(eventId),
                Criteria.where("status").is(PocPendingEventStatus.CLAIMED),
                Criteria.where("claimToken").is(claimToken)));
        PocPendingEventDocument requeued = template.findAndModify(query,
                new Update().set("status", PocPendingEventStatus.QUEUED)
                        .unset("claimToken").unset("claimedAt").unset("completedAt"),
                FindAndModifyOptions.options().returnNew(true), PocPendingEventDocument.class);
        return requeued == null ? Optional.empty() : Optional.of(toDomain(requeued));
    }

    private Optional<PocPendingEvent> claimRepositoryFallback(String eventId, String claimToken,
                                                               Instant now, Duration leaseTtl) {
        synchronized (repository) {
            return repository.findById(eventId).map(this::toDomain)
                    .filter(event -> event.claimableAt(now, leaseTtl))
                    .map(event -> repository.save(toDocument(event.claim(claimToken, now))))
                    .map(this::toDomain);
        }
    }

    private Optional<PocPendingEvent> requeueRepositoryFallback(String eventId, String claimToken, Instant now) {
        synchronized (repository) {
            return repository.findById(eventId).map(this::toDomain)
                    .filter(event -> event.status() == PocPendingEventStatus.CLAIMED
                            && claimToken != null && claimToken.equals(event.claimToken()))
                    .map(event -> repository.save(toDocument(event.requeue(now))))
                    .map(this::toDomain);
        }
    }

    private PocPendingEventDocument toDocument(PocPendingEvent event) {
        PocPendingEventDocument document = new PocPendingEventDocument();
        document.setEventId(event.eventId()); document.setContactId(event.contactId());
        document.setType(event.type()); document.setText(event.text()); document.setTranscript(event.transcript());
        document.setMediaFixture(event.mediaFixture()); document.setInteractiveReplyId(event.interactiveReplyId());
        document.setOccurredAt(event.occurredAt()); document.setProviderMessageId(event.providerMessageId());
        document.setAcceptedAt(event.acceptedAt()); document.setStatus(event.status());
        document.setClaimToken(event.claimToken()); document.setClaimedAt(event.claimedAt());
        document.setCompletedAt(event.completedAt());
        return document;
    }

    private PocPendingEvent toDomain(PocPendingEventDocument document) {
        return new PocPendingEvent(document.getEventId(), document.getContactId(), document.getType(),
                document.getText(), document.getTranscript(), document.getMediaFixture(),
                document.getInteractiveReplyId(), document.getOccurredAt(), document.getProviderMessageId(),
                document.getAcceptedAt(), document.getStatus(), document.getClaimToken(),
                document.getClaimedAt(), document.getCompletedAt());
    }
}
