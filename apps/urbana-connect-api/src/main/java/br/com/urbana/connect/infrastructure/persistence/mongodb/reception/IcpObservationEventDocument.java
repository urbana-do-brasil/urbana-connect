package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "reception_icp_observations")
@CompoundIndex(name = "reception_icp_observation_idempotency_unique",
        def = "{'idempotencyKey': 1}", unique = true)
public class IcpObservationEventDocument {
    @Id
    private String eventId;
    private String eventType;
    private String conversationId;
    private String turnId;
    private String serviceType;
    private List<String> missingFields;
    private String detectionPoint;
    private String idempotencyKey;
    private Instant occurredAt;
}
