package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.model.SessionLinkStatus;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.Optional;

/** Persists one contact document and embeds all prior session lineage. */
public class MongoAgentSessionLinkGateway implements AgentSessionLinkGateway {
    private final SpringDataAgentSessionLinkRepository repository;
    private final MongoTemplate template;

    public MongoAgentSessionLinkGateway(SpringDataAgentSessionLinkRepository repository) {
        this(repository, null);
    }

    public MongoAgentSessionLinkGateway(SpringDataAgentSessionLinkRepository repository, MongoTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    @Override
    public Optional<AgentSessionLink> findActiveByContactId(String contactId) {
        return repository.findByContactIdAndStatus(contactId, SessionLinkStatus.ACTIVE).map(this::toCurrentDomain);
    }

    @Override
    public Optional<AgentSessionLink> findBySessionId(String sessionId) {
        Optional<AgentSessionLinkDocument> current = repository.findByHermesSessionId(sessionId);
        if (current.isPresent()) {
            return Optional.of(toCurrentDomain(current.get()));
        }
        return repository.findByLineageHermesSessionId(sessionId)
                .flatMap(document -> document.getLineage() == null ? Optional.empty()
                        : document.getLineage().stream()
                        .filter(lineage -> sessionId.equals(lineage.getHermesSessionId()))
                        .findFirst()
                        .map(lineage -> toHistoricalDomain(document.getContactId(), lineage)));
    }

    @Override
    public AgentSessionLink createIfAbsent(AgentSessionLink link) {
        try {
            return toCurrentDomain(repository.insert(toDocument(link)));
        } catch (DuplicateKeyException concurrentCreate) {
            return findActiveByContactId(link.contactId())
                    .orElseThrow(() -> new IllegalStateException(
                            "session link create lost race and no active winner exists", concurrentCreate));
        }
    }

    @Override
    public AgentSessionLink touchActive(String contactId, String expectedSessionId, java.time.Instant lastUsedAt) {
        if (template == null) {
            AgentSessionLinkDocument current = repository.findById(contactId)
                    .filter(document -> expectedSessionId.equals(document.getHermesSessionId()))
                    .filter(document -> document.getStatus() == SessionLinkStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalStateException("active session link changed concurrently"));
            current.setLastUsedAt(lastUsedAt);
            current.setVersion(current.getVersion() + 1);
            return toCurrentDomain(repository.save(current));
        }
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(contactId),
                Criteria.where("hermesSessionId").is(expectedSessionId),
                Criteria.where("status").is(SessionLinkStatus.ACTIVE)));
        AgentSessionLinkDocument touched = template.findAndModify(query,
                new Update().set("lastUsedAt", lastUsedAt).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), AgentSessionLinkDocument.class);
        if (touched == null) {
            throw new IllegalStateException("active session link changed concurrently");
        }
        return toCurrentDomain(touched);
    }

    @Override
    public AgentSessionLink replaceActive(String contactId, String expectedSessionId,
                                           AgentSessionLink replacement, SessionLinkStatus previousStatus) {
        if (previousStatus != SessionLinkStatus.REPLACED && previousStatus != SessionLinkStatus.LOST) {
            throw new IllegalArgumentException("previous session must become REPLACED or LOST");
        }
        AgentSessionLinkDocument current = repository.findById(contactId)
                .filter(document -> expectedSessionId.equals(document.getHermesSessionId()))
                .filter(document -> document.getStatus() == SessionLinkStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("active session link changed concurrently"));
        AgentSessionLinkDocument.SessionLineageDocument previous = toLineage(
                current, replacement.hermesSessionId(), previousStatus);

        if (template == null) {
            // Test-only fallback; production wiring always supplies MongoTemplate
            // and executes the single conditional findAndModify below.
            current.setHermesSessionId(replacement.hermesSessionId());
            current.setStatus(SessionLinkStatus.ACTIVE);
            current.setCreatedAt(replacement.createdAt());
            current.setLastUsedAt(replacement.lastUsedAt());
            current.setReplacedBySessionId(null);
            current.setVersion(current.getVersion() + 1);
            appendLineage(current, previous);
            return toCurrentDomain(repository.save(current));
        }

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(contactId),
                Criteria.where("hermesSessionId").is(expectedSessionId),
                Criteria.where("status").is(SessionLinkStatus.ACTIVE),
                Criteria.where("version").is(current.getVersion())));
        Update update = currentProjectionUpdate(replacement, current.getVersion() + 1)
                .push("lineage", previous);
        AgentSessionLinkDocument updated = template.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), AgentSessionLinkDocument.class);
        if (updated == null) {
            throw new IllegalStateException("active session link changed concurrently");
        }
        return toCurrentDomain(updated);
    }

    private static Update currentProjectionUpdate(AgentSessionLink replacement, long version) {
        return new Update()
                .set("hermesSessionId", replacement.hermesSessionId())
                .set("status", SessionLinkStatus.ACTIVE)
                .set("createdAt", replacement.createdAt())
                .set("lastUsedAt", replacement.lastUsedAt())
                .set("replacedBySessionId", null)
                .set("version", version);
    }

    private AgentSessionLinkDocument toDocument(AgentSessionLink link) {
        AgentSessionLinkDocument document = new AgentSessionLinkDocument();
        document.setContactId(link.contactId());
        document.setHermesSessionId(link.hermesSessionId());
        document.setStatus(link.status());
        document.setCreatedAt(link.createdAt());
        document.setLastUsedAt(link.lastUsedAt());
        document.setReplacedBySessionId(link.replacedBySessionId());
        document.setVersion(link.version());
        document.setLineage(new ArrayList<>());
        return document;
    }

    private static AgentSessionLinkDocument.SessionLineageDocument toLineage(
            AgentSessionLinkDocument current, String replacementSessionId, SessionLinkStatus previousStatus) {
        return new AgentSessionLinkDocument.SessionLineageDocument(
                current.getHermesSessionId(), previousStatus, current.getCreatedAt(),
                current.getLastUsedAt(), replacementSessionId, current.getVersion() + 1);
    }

    private static void appendLineage(AgentSessionLinkDocument current,
                                      AgentSessionLinkDocument.SessionLineageDocument previous) {
        if (current.getLineage() == null) {
            current.setLineage(new ArrayList<>());
        }
        current.getLineage().add(previous);
    }

    private AgentSessionLink toCurrentDomain(AgentSessionLinkDocument document) {
        return new AgentSessionLink(document.getContactId(), document.getHermesSessionId(), document.getStatus(),
                document.getCreatedAt(), document.getLastUsedAt(), document.getReplacedBySessionId(),
                document.getVersion());
    }

    private AgentSessionLink toHistoricalDomain(String contactId,
                                                AgentSessionLinkDocument.SessionLineageDocument lineage) {
        return new AgentSessionLink(contactId, lineage.getHermesSessionId(), lineage.getStatus(),
                lineage.getCreatedAt(), lineage.getLastUsedAt(), lineage.getReplacedBySessionId(),
                lineage.getVersion());
    }
}
