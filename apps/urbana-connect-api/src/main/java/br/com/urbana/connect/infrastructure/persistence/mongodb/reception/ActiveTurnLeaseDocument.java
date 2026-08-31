package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "reception_active_turn_leases")
@CompoundIndex(name = "reception_lease_running_session", def = "{'hermesSessionId': 1, 'status': 1}", unique = true,
        partialFilter = "{'status': 'RUNNING'}")
public class ActiveTurnLeaseDocument {
    @Id
    private String hermesSessionId;
    @Indexed
    private String turnId;
    @Indexed
    private String contactId;
    private String sourceMessageId;
    private List<String> sourceMessageIds;
    private ActiveTurnLeaseStatus status;
    private Instant acquiredAt;
    // Keep EXPIRED/REVOKED tombstones. TTL deletion would allow a late
    // plugin call to become indistinguishable from a new turn.
    @Indexed
    private Instant expiresAt;
    private Instant revokedAt;
    private long version;
    private String claimToken;
}
