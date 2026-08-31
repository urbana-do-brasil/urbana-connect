package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;

/** Mongo adapter whose production path is compare-and-set only. */
public class MongoActiveTurnLeaseGateway implements ActiveTurnLeaseGateway {
    private final SpringDataActiveTurnLeaseRepository repository;
    private final MongoTemplate template;

    /** Repository-only constructor is retained for lightweight unit fakes. */
    public MongoActiveTurnLeaseGateway(SpringDataActiveTurnLeaseRepository repository) {
        this(repository, null);
    }

    public MongoActiveTurnLeaseGateway(SpringDataActiveTurnLeaseRepository repository, MongoTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    @Override
    public Optional<ActiveTurnLease> acquire(ActiveTurnLease requested) {
        if (template == null) {
            return acquireRepositoryFallback(requested);
        }

        // Only a cleanly REVOKED tombstone may be reused. EXPIRED (or any
        // unknown/in-doubt state) deliberately fails closed; a late plugin
        // call must never bind to a later turn on the same session.
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(requested.hermesSessionId()),
                Criteria.where("status").is(ActiveTurnLeaseStatus.REVOKED)));
        try {
            ActiveTurnLeaseDocument result = template.findAndModify(query, leaseUpdate(requested),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), ActiveTurnLeaseDocument.class);
            return result == null ? Optional.empty() : Optional.of(toDomain(result));
        } catch (DuplicateKeyException raceWithExistingLease) {
            // An absent row raced with another upsert, or an EXPIRED tombstone
            // prevented an unsafe replacement. Both are a rejected acquire.
            return Optional.empty();
        }
    }

    @Override
    public Optional<ActiveTurnLease> findRunning(String sessionId, Instant now) {
        require(sessionId, "sessionId");
        if (template == null) {
            return repository.findById(sessionId).map(this::toDomain).map(lease -> {
                if (lease.status() == ActiveTurnLeaseStatus.RUNNING && !now.isBefore(lease.expiresAt())) {
                    return toDomain(repository.save(toDocument(lease.expire(now))));
                }
                return lease;
            }).filter(lease -> lease.isActiveAt(now));
        }

        Query active = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId),
                Criteria.where("status").is(ActiveTurnLeaseStatus.RUNNING),
                Criteria.where("expiresAt").gt(now)));
        ActiveTurnLeaseDocument running = template.findAndModify(active,
                new Update().set("hermesSessionId", sessionId),
                FindAndModifyOptions.options().returnNew(true), ActiveTurnLeaseDocument.class);
        if (running != null) {
            return Optional.of(toDomain(running));
        }

        // Preserve the tombstone with a conditional expiration transition.
        Query expired = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId),
                Criteria.where("status").is(ActiveTurnLeaseStatus.RUNNING),
                Criteria.where("expiresAt").lte(now)));
        template.findAndModify(expired,
                new Update().set("status", ActiveTurnLeaseStatus.EXPIRED).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), ActiveTurnLeaseDocument.class);
        return Optional.empty();
    }

    @Override
    public Optional<ActiveTurnLease> findBlocking(String sessionId, Instant now) {
        require(sessionId, "sessionId");
        if (template == null) {
            return repository.findById(sessionId).map(this::toDomain)
                    .filter(lease -> lease.blocksNewTurnAt(now));
        }
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId),
                Criteria.where("status").in(ActiveTurnLeaseStatus.RUNNING, ActiveTurnLeaseStatus.RECONCILING),
                new Criteria().orOperator(
                        Criteria.where("status").is(ActiveTurnLeaseStatus.RECONCILING),
                        Criteria.where("expiresAt").gt(now))));
        ActiveTurnLeaseDocument document = template.findOne(query, ActiveTurnLeaseDocument.class);
        return document == null ? Optional.empty() : Optional.of(toDomain(document));
    }

    @Override
    public ActiveTurnLease revoke(String sessionId, String turnId, Instant now) {
        return revoke(sessionId, turnId, null, now);
    }

    @Override
    public ActiveTurnLease revoke(String sessionId, String turnId, String claimToken, Instant now) {
        require(sessionId, "sessionId");
        require(turnId, "turnId");
        if (template == null) {
            ActiveTurnLease lease = repository.findById(sessionId).map(this::toDomain)
                    .orElseThrow(() -> new IllegalArgumentException("lease not found"));
            if (!lease.turnId().equals(turnId)) {
                throw new IllegalArgumentException("lease turn binding mismatch");
            }
            if (claimToken != null && !claimToken.equals(lease.claimToken())) {
                throw new IllegalArgumentException("lease claim binding mismatch");
            }
            if (lease.status() != ActiveTurnLeaseStatus.RUNNING
                    && lease.status() != ActiveTurnLeaseStatus.RECONCILING) {
                return lease;
            }
            ActiveTurnLease next = lease.status() == ActiveTurnLeaseStatus.RECONCILING
                    || now.isBefore(lease.expiresAt()) ? lease.revoke(now) : lease.expire(now);
            return toDomain(repository.save(toDocument(next)));
        }

        java.util.List<Criteria> predicates = new java.util.ArrayList<>(java.util.List.of(
                Criteria.where("_id").is(sessionId),
                Criteria.where("turnId").is(turnId),
                Criteria.where("status").in(ActiveTurnLeaseStatus.RUNNING, ActiveTurnLeaseStatus.RECONCILING)));
        if (claimToken != null) predicates.add(Criteria.where("claimToken").is(claimToken));
        Query query = new Query(new Criteria().andOperator(predicates.toArray(Criteria[]::new)));
        ActiveTurnLeaseDocument result = template.findAndModify(query,
                new Update().set("status", ActiveTurnLeaseStatus.REVOKED)
                        .set("revokedAt", now).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), ActiveTurnLeaseDocument.class);
        if (result == null) {
            Query expired = new Query(new Criteria().andOperator(
                    Criteria.where("_id").is(sessionId),
                    Criteria.where("turnId").is(turnId),
                    Criteria.where("status").is(ActiveTurnLeaseStatus.RUNNING),
                    Criteria.where("expiresAt").lte(now)));
            ActiveTurnLeaseDocument expiredResult = template.findAndModify(expired,
                    new Update().set("status", ActiveTurnLeaseStatus.EXPIRED).inc("version", 1),
                    FindAndModifyOptions.options().returnNew(true), ActiveTurnLeaseDocument.class);
            if (expiredResult != null) {
                return toDomain(expiredResult);
            }
            ActiveTurnLeaseDocument terminal = template.findOne(terminalLeaseQuery(sessionId, turnId),
                    ActiveTurnLeaseDocument.class);
            if (terminal != null) {
                return toDomain(terminal);
            }
            throw new IllegalArgumentException("lease is not active or turn binding mismatch");
        }
        return toDomain(result);
    }

    @Override
    public ActiveTurnLease markReconciling(String sessionId, String turnId, String claimToken, Instant now) {
        require(sessionId, "sessionId");
        require(turnId, "turnId");
        if (template == null) {
            ActiveTurnLease lease = repository.findById(sessionId).map(this::toDomain)
                    .orElseThrow(() -> new IllegalArgumentException("lease not found"));
            if (!lease.turnId().equals(turnId) || !lease.claimToken().equals(claimToken)) {
                throw new IllegalArgumentException("lease claim binding mismatch");
            }
            return toDomain(repository.save(toDocument(lease.reconcile(now))));
        }
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId), Criteria.where("turnId").is(turnId),
                Criteria.where("claimToken").is(claimToken), Criteria.where("status").is(ActiveTurnLeaseStatus.RUNNING)));
        ActiveTurnLeaseDocument result = template.findAndModify(query,
                new Update().set("status", ActiveTurnLeaseStatus.RECONCILING).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), ActiveTurnLeaseDocument.class);
        if (result == null) throw new IllegalArgumentException("lease is not running or claim is stale");
        return toDomain(result);
    }

    @Override
    public ActiveTurnLease heartbeat(String sessionId, String turnId, String claimToken,
                                     Instant now, Duration ttl) {
        require(sessionId, "sessionId");
        require(turnId, "turnId");
        if (template == null) {
            ActiveTurnLease lease = repository.findById(sessionId).map(this::toDomain)
                    .orElseThrow(() -> new IllegalArgumentException("lease not found"));
            if (!lease.turnId().equals(turnId) || !lease.claimToken().equals(claimToken)) {
                throw new IllegalArgumentException("lease claim binding mismatch");
            }
            return toDomain(repository.save(toDocument(lease.heartbeat(now, ttl))));
        }
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId), Criteria.where("turnId").is(turnId),
                Criteria.where("claimToken").is(claimToken),
                Criteria.where("status").in(ActiveTurnLeaseStatus.RUNNING, ActiveTurnLeaseStatus.RECONCILING)));
        ActiveTurnLeaseDocument result = template.findAndModify(query,
                new Update().set("expiresAt", now.plus(ttl)).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), ActiveTurnLeaseDocument.class);
        if (result == null) throw new IllegalArgumentException("lease heartbeat rejected");
        return toDomain(result);
    }

    @Override
    public ActiveTurnLease expire(String sessionId, String turnId, Instant now) {
        require(sessionId, "sessionId");
        require(turnId, "turnId");
        if (template == null) {
            ActiveTurnLease lease = repository.findById(sessionId).map(this::toDomain)
                    .orElseThrow(() -> new IllegalArgumentException("lease not found"));
            if (!lease.turnId().equals(turnId)) {
                throw new IllegalArgumentException("lease turn binding mismatch");
            }
            if (lease.status() != ActiveTurnLeaseStatus.RUNNING) {
                return lease;
            }
            return toDomain(repository.save(toDocument(lease.expire(now))));
        }

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId),
                Criteria.where("turnId").is(turnId),
                Criteria.where("status").is(ActiveTurnLeaseStatus.RUNNING),
                Criteria.where("expiresAt").lte(now)));
        ActiveTurnLeaseDocument result = template.findAndModify(query,
                new Update().set("status", ActiveTurnLeaseStatus.EXPIRED).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), ActiveTurnLeaseDocument.class);
        if (result == null) {
            ActiveTurnLeaseDocument terminal = template.findOne(terminalLeaseQuery(sessionId, turnId),
                    ActiveTurnLeaseDocument.class);
            if (terminal != null) {
                return toDomain(terminal);
            }
            throw new IllegalArgumentException("lease is not expired or turn binding mismatch");
        }
        return toDomain(result);
    }

    private Optional<ActiveTurnLease> acquireRepositoryFallback(ActiveTurnLease requested) {
        Optional<ActiveTurnLeaseDocument> current = repository.findById(requested.hermesSessionId());
        if (current.isPresent()) {
            ActiveTurnLease existing = toDomain(current.get());
            if (existing.status() != ActiveTurnLeaseStatus.REVOKED) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(toDomain(repository.save(toDocument(requested))));
        } catch (DuplicateKeyException race) {
            return Optional.empty();
        }
    }

    private static Update leaseUpdate(ActiveTurnLease lease) {
        return new Update()
                .set("hermesSessionId", lease.hermesSessionId())
                .set("turnId", lease.turnId())
                .set("contactId", lease.contactId())
                .set("sourceMessageId", lease.sourceMessageId())
                .set("sourceMessageIds", lease.sourceMessageIds())
                .set("status", lease.status())
                .set("acquiredAt", lease.acquiredAt())
                .set("expiresAt", lease.expiresAt())
                .set("revokedAt", lease.revokedAt())
                .set("version", lease.version())
                .set("claimToken", lease.claimToken());
    }

    private static Query terminalLeaseQuery(String sessionId, String turnId) {
        return new Query(new Criteria().andOperator(
                Criteria.where("_id").is(sessionId),
                Criteria.where("turnId").is(turnId),
                Criteria.where("status").in(ActiveTurnLeaseStatus.REVOKED, ActiveTurnLeaseStatus.EXPIRED)));
    }

    private ActiveTurnLeaseDocument toDocument(ActiveTurnLease lease) {
        ActiveTurnLeaseDocument document = new ActiveTurnLeaseDocument();
        document.setHermesSessionId(lease.hermesSessionId());
        document.setTurnId(lease.turnId());
        document.setContactId(lease.contactId());
        document.setSourceMessageId(lease.sourceMessageId());
        document.setSourceMessageIds(lease.sourceMessageIds());
        document.setStatus(lease.status());
        document.setAcquiredAt(lease.acquiredAt());
        document.setExpiresAt(lease.expiresAt());
        document.setRevokedAt(lease.revokedAt());
        document.setVersion(lease.version());
        document.setClaimToken(lease.claimToken());
        return document;
    }

    private ActiveTurnLease toDomain(ActiveTurnLeaseDocument document) {
        return new ActiveTurnLease(document.getHermesSessionId(), document.getTurnId(), document.getContactId(),
                document.getSourceMessageId(), document.getStatus(), document.getAcquiredAt(),
                document.getExpiresAt(), document.getRevokedAt(), document.getVersion(), document.getClaimToken(),
                document.getSourceMessageIds() == null || document.getSourceMessageIds().isEmpty()
                        ? java.util.List.of(document.getSourceMessageId()) : document.getSourceMessageIds());
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
