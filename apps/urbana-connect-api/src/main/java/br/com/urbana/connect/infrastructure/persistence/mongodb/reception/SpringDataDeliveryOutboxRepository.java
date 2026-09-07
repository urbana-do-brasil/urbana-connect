package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.urbana.connect.domain.reception.model.DeliveryOutboxStatus;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface SpringDataDeliveryOutboxRepository extends MongoRepository<DeliveryOutboxDocument, String> {
    List<DeliveryOutboxDocument> findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            List<DeliveryOutboxStatus> statuses, Instant now, Pageable pageable);

    List<DeliveryOutboxDocument> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            DeliveryOutboxStatus status, Instant now, Pageable pageable);
}
