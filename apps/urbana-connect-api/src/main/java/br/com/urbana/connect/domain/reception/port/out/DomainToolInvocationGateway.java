package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;

import java.util.Optional;
import java.util.List;

public interface DomainToolInvocationGateway {
    Optional<DomainToolInvocation> findByIdempotencyKey(String idempotencyKey);

    DomainToolInvocation save(DomainToolInvocation invocation);

    default List<DomainToolInvocation> findByTurnId(String turnId) {
        return List.of();
    }

    default List<DomainToolInvocation> findByContactId(String contactId) {
        return List.of();
    }
}
