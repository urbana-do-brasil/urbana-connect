package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.SessionLinkStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface SpringDataAgentSessionLinkRepository extends MongoRepository<AgentSessionLinkDocument, String> {
    Optional<AgentSessionLinkDocument> findByContactIdAndStatus(String contactId, SessionLinkStatus status);
    Optional<AgentSessionLinkDocument> findByHermesSessionId(String hermesSessionId);

    @Query("{'lineage.hermesSessionId': ?0}")
    Optional<AgentSessionLinkDocument> findByLineageHermesSessionId(String hermesSessionId);
}
