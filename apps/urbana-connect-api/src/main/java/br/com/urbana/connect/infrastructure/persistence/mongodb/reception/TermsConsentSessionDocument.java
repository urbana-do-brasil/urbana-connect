package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentDecision;
import br.com.urbana.connect.domain.reception.model.TermsConsentSessionStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Mongo projection for an ephemeral web-consent session. */
@Data
@Document(collection = "reception_terms_consent_sessions")
@CompoundIndex(name = "terms_session_conversation_active",
        def = "{'conversationId': 1, 'contractingUnitId': 1, 'status': 1}")
public class TermsConsentSessionDocument {
    @Id
    private String presentationId;
    @Indexed(unique = true)
    private String tokenDigest;
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
    @Indexed
    private String conversationId;
    private String contactId;
    private String destinationRef;
    private String contractingUnitId;
    private String environment;
    private String serviceType;
    private String termsVersion;
    private String termsHash;
    private String termsResource;
    private String termsContent;
    private Instant issuedAt;
    private TermsConsentSessionStatus status;
    private Instant pagePresentedAt;
    private Instant endReachedAt;
    private TermsConsentDecision decision;
    private Instant decidedAt;
    private String decisionEventId;
    private long version;
}
