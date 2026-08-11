package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.PocPendingEventStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "poc_pending_events")
@CompoundIndex(name = "poc_pending_contact_status_order", def = "{'contactId': 1, 'status': 1, 'occurredAt': 1}")
public class PocPendingEventDocument {
    @Id
    private String eventId;
    @Indexed
    private String contactId;
    private ReceptionMessageType type;
    private String text;
    private String transcript;
    private String mediaFixture;
    private String interactiveReplyId;
    private Instant occurredAt;
    private String providerMessageId;
    private Instant acceptedAt;
    private PocPendingEventStatus status;
    private String claimToken;
    private Instant claimedAt;
    private Instant completedAt;
}
