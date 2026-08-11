package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataActiveTurnLeaseRepository extends MongoRepository<ActiveTurnLeaseDocument, String> {
    long countByHermesSessionIdAndStatus(String hermesSessionId, ActiveTurnLeaseStatus status);
}
