package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.PocPendingEventStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringDataPocPendingEventRepository extends MongoRepository<PocPendingEventDocument, String> {
    List<PocPendingEventDocument> findByStatusIn(List<PocPendingEventStatus> statuses);

    List<PocPendingEventDocument> findByStatusInOrderByContactIdAscOccurredAtAscAcceptedAtAscEventIdAsc(
            List<PocPendingEventStatus> statuses);

    List<PocPendingEventDocument> findByContactIdOrderByOccurredAtAscAcceptedAtAscEventIdAsc(String contactId);

    List<PocPendingEventDocument> findByContactId(String contactId);
}
