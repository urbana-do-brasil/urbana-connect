package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.CommercialStage;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.ResumeStatus;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "reception_conversations")
public class ReceptionConversationDocument {
    @Id
    private String id;
    @Indexed(unique = true)
    private String contactId;
    private ReceptionMode mode;
    private CommercialStage commercialStage;
    private String selectedService;
    private TermsStatus termsStatus;
    private PaymentStatus paymentStatus;
    private String handoffReason;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;
    private ResumeStatus resumeStatus;
    private String resumeId;
    private String resumeIdempotencyKey;
    private String resumeChecksum;
    private int resumeBoundarySequence;
    private String resumeDecisionAction;
    private String resumeDecisionMessage;
    private String resumeFailureCode;
}
