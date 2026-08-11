package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "reception_messages")
@CompoundIndex(name = "reception_message_event_unique", def = "{'eventId': 1}", unique = true)
public class ReceptionMessageDocument {
    @Id
    private String id;
    private String eventId;
    @Indexed
    private String correlationId;
    @Indexed
    private String conversationId;
    @Indexed
    private String contactId;
    private ReceptionMessageDirection direction;
    private ReceptionMessageSender senderType;
    private ReceptionMessageType type;
    private String text;
    private String mediaRef;
    private String providerMessageId;
    private Instant createdAt;
}
