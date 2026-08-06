package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "reception_turns")
public class ReceptionTurnDocument {
    @Id
    private String id;
    @Indexed(unique = true)
    private String correlationId;
    @Indexed
    private String contactId;
    private String hermesSessionId;
    private List<String> inboundMessageIds = new ArrayList<>();
    private ReceptionTurnStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private String failureCode;
    private String outputMessage;
    private AgentNextAction outputNextAction;
    private String outputHandoffReason;
}
