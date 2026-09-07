package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxStatus;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;

/** Idempotent outbox writer; no channel is called by this adapter. */
public final class MongoDeliveryOutboxGateway implements DeliveryOutboxGateway {
    private final SpringDataDeliveryOutboxRepository repository;
    private final MongoTemplate template;

    public MongoDeliveryOutboxGateway(SpringDataDeliveryOutboxRepository repository) {
        this(repository, null);
    }

    public MongoDeliveryOutboxGateway(SpringDataDeliveryOutboxRepository repository, MongoTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    @Override
    public DeliveryOutbox saveIfAbsent(DeliveryOutbox outbox) {
        Optional<DeliveryOutboxDocument> existing = repository.findById(outbox.eventKey());
        if (existing.isPresent()) {
            return toDomain(existing.orElseThrow());
        }
        try {
            return toDomain(repository.insert(toDocument(outbox)));
        } catch (DuplicateKeyException race) {
            return repository.findById(outbox.eventKey()).map(MongoDeliveryOutboxGateway::toDomain)
                    .orElseThrow(() -> new IllegalStateException("outbox entry lost during idempotent save", race));
        }
    }

    @Override
    public Optional<DeliveryOutbox> findByEventKey(String eventKey) {
        return repository.findById(eventKey).map(MongoDeliveryOutboxGateway::toDomain);
    }

    @Override
    public List<DeliveryOutbox> findDue(Instant now, int limit) {
        if (now == null || limit <= 0) {
            return List.of();
        }
        return repository.findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        List.of(DeliveryOutboxStatus.PENDING, DeliveryOutboxStatus.RETRYABLE), now,
                        PageRequest.of(0, Math.min(limit, 100)))
                .stream().map(MongoDeliveryOutboxGateway::toDomain).toList();
    }

    @Override
    public Optional<DeliveryOutbox> claim(String eventKey, Instant now) {
        if (eventKey == null || eventKey.isBlank() || now == null) {
            return Optional.empty();
        }
        if (template == null) {
            return findByEventKey(eventKey)
                    .filter(value -> value.status() == DeliveryOutboxStatus.PENDING
                            || value.status() == DeliveryOutboxStatus.RETRYABLE)
                    .map(value -> {
                        DeliveryOutbox sending = value.sending(now);
                        repository.save(toDocument(sending));
                        return sending;
                    });
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(eventKey),
                Criteria.where("status").in(DeliveryOutboxStatus.PENDING, DeliveryOutboxStatus.RETRYABLE),
                Criteria.where("nextAttemptAt").lte(now)));
        Update update = new Update().set("status", DeliveryOutboxStatus.SENDING)
                .inc("attempts", 1).set("nextAttemptAt", now);
        DeliveryOutboxDocument claimed = template.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), DeliveryOutboxDocument.class);
        return Optional.ofNullable(claimed).map(MongoDeliveryOutboxGateway::toDomain);
    }

    @Override
    public int recoverStale(Instant now, Duration lease, int limit) {
        if (now == null || lease == null || lease.isNegative() || lease.isZero() || limit <= 0) {
            return 0;
        }
        Instant cutoff = now.minus(lease);
        int recovered = 0;
        if (template == null) {
            List<DeliveryOutboxDocument> stale = repository
                    .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                            DeliveryOutboxStatus.SENDING, cutoff, PageRequest.of(0, Math.min(limit, 100)));
            for (DeliveryOutboxDocument document : stale) {
                DeliveryOutbox value = toDomain(document).ambiguous(now);
                repository.save(toDocument(value));
                recovered++;
            }
            return recovered;
        }

        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("status").is(DeliveryOutboxStatus.SENDING),
                Criteria.where("nextAttemptAt").lte(cutoff)))
                .with(Sort.by(Sort.Direction.ASC, "nextAttemptAt"));
        Update update = new Update().set("status", DeliveryOutboxStatus.AMBIGUOUS)
                .set("nextAttemptAt", now);
        while (recovered < Math.min(limit, 100)) {
            DeliveryOutboxDocument value = template.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true), DeliveryOutboxDocument.class);
            if (value == null) {
                break;
            }
            recovered++;
        }
        return recovered;
    }

    @Override
    public DeliveryOutbox update(DeliveryOutbox outbox) {
        return toDomain(repository.save(toDocument(outbox)));
    }

    private static DeliveryOutboxDocument toDocument(DeliveryOutbox value) {
        DeliveryOutboxDocument document = new DeliveryOutboxDocument();
        document.setEventKey(value.eventKey()); document.setKind(value.kind());
        document.setDestinationRef(value.destinationRef()); document.setPayload(value.payload());
        document.setStatus(value.status()); document.setAttempts(value.attempts());
        document.setNextAttemptAt(value.nextAttemptAt()); document.setProviderMessageId(value.providerMessageId());
        document.setCorrelationId(value.correlationId()); document.setCreatedAt(value.createdAt());
        document.setSentAt(value.sentAt());
        return document;
    }

    private static DeliveryOutbox toDomain(DeliveryOutboxDocument value) {
        return new DeliveryOutbox(value.getEventKey(), value.getKind(), value.getDestinationRef(), value.getPayload(),
                value.getStatus(), value.getAttempts(), value.getNextAttemptAt(), value.getProviderMessageId(),
                value.getCorrelationId(), value.getCreatedAt(), value.getSentAt());
    }
}
