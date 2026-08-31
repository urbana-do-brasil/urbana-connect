package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentAudit;
import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditGateway;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** Application boundary that prevents an acceptance without durable presentation evidence. */
public class TermsAcceptanceUseCase {
    private final TermsConsentAuditGateway audits;
    private final ReceptionConversationGateway conversations;

    /** Compatibility constructor for isolated domain tests and legacy callers. */
    public TermsAcceptanceUseCase(TermsConsentAuditGateway audits) {
        this(audits, null);
    }

    public TermsAcceptanceUseCase(TermsConsentAuditGateway audits,
                                  ReceptionConversationGateway conversations) {
        this.audits = Objects.requireNonNull(audits);
        this.conversations = conversations;
    }

    public TermsConsentAudit recordAcceptance(String presentationId, String eventId, String messageId,
                                              String exactText, long conversationVersion, Instant now) {
        require(presentationId, "presentationId");
        require(eventId, "eventId");
        require(messageId, "messageId");
        require(exactText, "exactText");
        if (conversationVersion < 0) {
            throw new IllegalArgumentException("conversationVersion must be non-negative");
        }
        Objects.requireNonNull(now, "now");
        return audits.acceptIfPresented(presentationId, eventId, messageId, exactText, conversationVersion, now);
    }

    public TermsConsentAudit recordPresentation(TermsConsentAudit presentation) {
        if (presentation.status() != br.com.urbana.connect.domain.reception.model.TermsConsentStatus.PRESENTED) {
            throw new IllegalArgumentException("only a presented audit can be recorded as presentation evidence");
        }
        return audits.savePresentationIfAbsent(presentation);
    }

    /**
     * Applies the commercial transition only after the CAS-backed evidence was
     * accepted. Legacy conversations without an active presentation are
     * intentionally rejected so terms are presented again instead of inferred.
     */
    @Transactional
    public ReceptionConversation recordAcceptance(ReceptionConversation conversation, String eventId,
                                                  String messageId, String exactText, Instant now,
                                                  CommercialPolicyService policy) {
        Objects.requireNonNull(conversation, "conversation");
        if (conversation.termsStatus() != TermsStatus.PRESENTED || conversation.activeTermsConsentId() == null) {
            throw new IllegalStateException("terms must be presented with durable evidence before acceptance");
        }
        TermsConsentAudit presented = audits.findByPresentationId(conversation.activeTermsConsentId())
                .orElseThrow(() -> new IllegalStateException("terms presentation evidence is missing"));
        if ((presented.status() != TermsConsentStatus.PRESENTED
                && presented.status() != TermsConsentStatus.ACCEPTED)
                || !conversation.id().equals(presented.conversationId())
                || !Objects.equals(conversation.contractingUnitId(), presented.contractingUnitId())
                || !Objects.equals(conversation.selectedService(), presented.serviceType())) {
            throw new IllegalStateException("terms presentation evidence does not match the current contracting unit");
        }
        if (presented.status() == TermsConsentStatus.PRESENTED) {
            recordAcceptance(presented.presentationId(), eventId, messageId, exactText,
                    conversation.version(), now);
        }
        ReceptionConversation accepted = policy.acceptTerms(conversation, exactText, now);
        return conversations == null ? accepted : conversations.save(accepted);
    }

    /** True when the production adapter owns the conversation write in the transaction. */
    public boolean persistsConversation() {
        return conversations != null;
    }

    /** Fails closed when a conversation points at missing or incomplete evidence. */
    public TermsConsentAudit requireAcceptedEvidence(ReceptionConversation conversation) {
        Objects.requireNonNull(conversation, "conversation");
        if (conversation.activeTermsConsentId() == null) {
            throw new IllegalStateException("durable terms presentation evidence is missing");
        }
        TermsConsentAudit audit = audits.findByPresentationId(conversation.activeTermsConsentId())
                .orElseThrow(() -> new IllegalStateException("durable terms presentation evidence is missing"));
        if (audit.status() != TermsConsentStatus.ACCEPTED
                || !conversation.id().equals(audit.conversationId())
                || !Objects.equals(conversation.contractingUnitId(), audit.contractingUnitId())
                || !Objects.equals(conversation.selectedService(), audit.serviceType())) {
            throw new IllegalStateException("durable terms acceptance evidence does not match the current contracting unit");
        }
        return audit;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
