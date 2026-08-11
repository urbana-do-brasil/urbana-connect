package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.DomainToolInvocationStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "reception_domain_tool_invocations")
public class DomainToolInvocationDocument {
    @Id
    private String id;
    @Indexed(unique = true)
    private String idempotencyKey;
    @Indexed
    private String turnId;
    private String hermesSessionId;
    private String contactId;
    private DomainToolName toolName;
    private String argumentsHash;
    private DomainToolInvocationStatus status;
    private String resultCode;
    private Object resultPayload;
    private Instant createdAt;
    private Instant finishedAt;
}
