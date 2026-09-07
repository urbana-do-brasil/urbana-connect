package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "reception_delivery_outbox")
public class DeliveryOutboxDocument {
    @Id
    private String eventKey;
    private DeliveryOutboxKind kind;
    @Indexed
    private String destinationRef;
    private String payload;
    @Indexed
    private DeliveryOutboxStatus status;
    private int attempts;
    @Indexed
    private Instant nextAttemptAt;
    private String providerMessageId;
    private String correlationId;
    private Instant createdAt;
    private Instant sentAt;
}
