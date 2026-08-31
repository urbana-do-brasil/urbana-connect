package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "reception_terms_consent_audits")
@CompoundIndex(name = "conversation_unit_status", def = "{'conversationId': 1, 'contractingUnitId': 1, 'status': 1}")
public class TermsConsentAuditDocument {
    @Id
    private String presentationId;
    @Indexed
    private String conversationId;
    private String contactId;
    private String turnId;
    private String contractingUnitId;
    private String environmentLabelSnapshot;
    private String environmentSourceMessageId;
    private String serviceType;
    private String termsResource;
    private String termsVersion;
    @Indexed(unique = true, sparse = true)
    private String prepareTermsInvocationId;
    private String termsOutboundMessageId;
    private Instant presentedAt;
    private String acceptanceMessageId;
    @Indexed(unique = true, sparse = true)
    private String acceptanceEventId;
    private String acceptanceTextExact;
    private Instant acceptedAt;
    private Instant recordedAt;
    private TermsConsentStatus status;
    private long conversationVersionAtPresentation;
    private Long conversationVersionAtAcceptance;
}
