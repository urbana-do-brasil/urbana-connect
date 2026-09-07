package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentAuditEventType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Append-only Mongo document; unlike sessions it has no TTL index. */
@Data
@Document(collection = "reception_terms_consent_events")
public class TermsConsentAuditEventDocument {
    @Id
    private String eventId;
    @Indexed
    private String presentationId;
    private TermsConsentAuditEventType eventType;
    private String conversationId;
    private String contactId;
    private String destinationRef;
    private String contractingUnitId;
    private String environment;
    private String serviceType;
    private String termsVersion;
    private String termsHash;
    private String termsResource;
    private Instant occurredAt;
    private String decisionEventId;
}
