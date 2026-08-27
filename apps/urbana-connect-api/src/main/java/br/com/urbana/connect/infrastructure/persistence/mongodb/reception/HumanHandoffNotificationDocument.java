package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "reception_handoff_notifications")
@CompoundIndex(name = "reception_handoff_notification_idempotency_unique",
        def = "{'idempotencyKey': 1}", unique = true)
public class HumanHandoffNotificationDocument {
    @Id
    private String notificationId;
    private String idempotencyKey;
    private String conversationId;
    private String turnId;
    private String reason;
    private String serviceType;
    private String commercialStage;
    private String paymentStatus;
    private List<String> presentIcpFields;
    private List<String> missingIcpFields;
    private Instant occurredAt;
}
