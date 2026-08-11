package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;

import java.util.Optional;
import java.util.List;

public class MongoDomainToolInvocationGateway implements DomainToolInvocationGateway {
    private final SpringDataDomainToolInvocationRepository repository;

    public MongoDomainToolInvocationGateway(SpringDataDomainToolInvocationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DomainToolInvocation> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public DomainToolInvocation save(DomainToolInvocation invocation) {
        return toDomain(repository.save(toDocument(invocation)));
    }

    @Override
    public List<DomainToolInvocation> findByTurnId(String turnId) {
        return repository.findByTurnId(turnId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<DomainToolInvocation> findByContactId(String contactId) {
        return repository.findByContactIdOrderByCreatedAtAsc(contactId).stream()
                .map(this::toDomain).toList();
    }

    private DomainToolInvocationDocument toDocument(DomainToolInvocation i) {
        DomainToolInvocationDocument d = new DomainToolInvocationDocument(); d.setId(i.id());
        d.setIdempotencyKey(i.idempotencyKey()); d.setTurnId(i.turnId()); d.setHermesSessionId(i.hermesSessionId());
        d.setContactId(i.contactId()); d.setToolName(i.toolName()); d.setArgumentsHash(i.argumentsHash());
        d.setStatus(i.status()); d.setResultCode(i.resultCode()); d.setResultPayload(i.resultPayload());
        d.setCreatedAt(i.createdAt()); d.setFinishedAt(i.finishedAt()); return d;
    }

    private DomainToolInvocation toDomain(DomainToolInvocationDocument d) {
        return new DomainToolInvocation(d.getId(), d.getIdempotencyKey(), d.getTurnId(), d.getHermesSessionId(),
                d.getContactId(), d.getToolName(), d.getArgumentsHash(), d.getStatus(), d.getResultCode(),
                d.getResultPayload(), d.getCreatedAt(), d.getFinishedAt());
    }
}
