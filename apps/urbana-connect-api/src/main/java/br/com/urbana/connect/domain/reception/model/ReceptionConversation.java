package br.com.urbana.connect.domain.reception.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Operational state owned by Urbana Connect, independent from Hermes. */
public record ReceptionConversation(
        String id,
        String contactId,
        ReceptionMode mode,
        CommercialStage commercialStage,
        String selectedService,
        TermsStatus termsStatus,
        PaymentStatus paymentStatus,
        String handoffReason,
        Instant createdAt,
        Instant updatedAt,
        long version,
        ResumeStatus resumeStatus,
        String resumeId,
        String resumeIdempotencyKey,
        String resumeChecksum,
        int resumeBoundarySequence,
        String resumeDecisionAction,
        String resumeDecisionMessage,
        String resumeFailureCode,
        String contractingUnitId,
        String environmentLabel,
        String environmentSourceMessageId,
        String activeTermsConsentId) {

    /** Compatibility constructor for persisted conversations created before unit binding. */
    public ReceptionConversation(String id, String contactId, ReceptionMode mode,
                                 CommercialStage commercialStage, String selectedService,
                                 TermsStatus termsStatus, PaymentStatus paymentStatus,
                                 String handoffReason, Instant createdAt, Instant updatedAt,
                                 long version, ResumeStatus resumeStatus, String resumeId,
                                 String resumeIdempotencyKey, String resumeChecksum,
                                 int resumeBoundarySequence, String resumeDecisionAction,
                                 String resumeDecisionMessage, String resumeFailureCode) {
        this(id, contactId, mode, commercialStage, selectedService, termsStatus, paymentStatus,
                handoffReason, createdAt, updatedAt, version, resumeStatus, resumeId,
                resumeIdempotencyKey, resumeChecksum, resumeBoundarySequence, resumeDecisionAction,
                resumeDecisionMessage, resumeFailureCode, null, null, null, null);
    }

    public ReceptionConversation(String id, String contactId, ReceptionMode mode,
                                 CommercialStage commercialStage, String selectedService,
                                 TermsStatus termsStatus, PaymentStatus paymentStatus,
                                 String handoffReason, Instant createdAt, Instant updatedAt,
                                 long version) {
        this(id, contactId, mode, commercialStage, selectedService, termsStatus, paymentStatus,
                handoffReason, createdAt, updatedAt, version, ResumeStatus.NONE, null, null,
                null, 0, null, null, null);
    }

    public ReceptionConversation {
        id = require(id, "id");
        contactId = require(contactId, "contactId");
        mode = Objects.requireNonNull(mode, "mode");
        commercialStage = Objects.requireNonNull(commercialStage, "commercialStage");
        termsStatus = Objects.requireNonNull(termsStatus, "termsStatus");
        paymentStatus = Objects.requireNonNull(paymentStatus, "paymentStatus");
        resumeStatus = resumeStatus == null ? ResumeStatus.NONE : resumeStatus;
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (mode == ReceptionMode.HUMAN && (handoffReason == null || handoffReason.isBlank())) {
            throw new IllegalArgumentException("handoffReason is required in HUMAN mode");
        }
        if (paymentStatus != PaymentStatus.NOT_STARTED && selectedService == null) {
            throw new IllegalArgumentException("payment state requires a selected service");
        }
        if (resumeBoundarySequence < 0) {
            throw new IllegalArgumentException("resume boundary must be non-negative");
        }
    }

    public static ReceptionConversation start(String contactId, Instant now) {
        return start(UUID.randomUUID().toString(), contactId, now);
    }

    public static ReceptionConversation start(String id, String contactId, Instant now) {
        return new ReceptionConversation(id, contactId, ReceptionMode.AI, CommercialStage.DISCOVERY,
                null, TermsStatus.NOT_PRESENTED, PaymentStatus.NOT_STARTED, null, now, now, 0);
    }

    public boolean isHuman() {
        return mode == ReceptionMode.HUMAN;
    }

    public boolean canPreparePayment(boolean ignoredIcpComplete) {
        return mode == ReceptionMode.AI && selectedService != null
                && termsStatus == TermsStatus.ACCEPTED;
    }

    public ReceptionConversation selectService(String service, Instant now) {
        require(service, "service");
        boolean changed = !Objects.equals(selectedService, service);
        return copy(mode, CommercialStage.ICP, service,
                changed ? TermsStatus.NOT_PRESENTED : termsStatus,
                changed ? PaymentStatus.NOT_STARTED : paymentStatus,
                handoffReason, now);
    }

    public ReceptionConversation bindContractingUnit(String unitId, String label, String sourceMessageId, Instant now) {
        require(unitId, "contractingUnitId"); require(label, "environmentLabel"); require(sourceMessageId, "environmentSourceMessageId");
        if (Objects.equals(contractingUnitId, unitId)) return this;
        return new ReceptionConversation(id, contactId, mode, CommercialStage.DISCOVERY, null,
                TermsStatus.NOT_PRESENTED, PaymentStatus.NOT_STARTED, handoffReason, createdAt, now, version + 1,
                resumeStatus, resumeId, resumeIdempotencyKey, resumeChecksum, resumeBoundarySequence,
                resumeDecisionAction, resumeDecisionMessage, resumeFailureCode, unitId, label, sourceMessageId, null);
    }

    public ReceptionConversation activateTermsConsent(String consentId, Instant now) {
        require(consentId, "activeTermsConsentId");
        return new ReceptionConversation(id, contactId, mode, commercialStage, selectedService, termsStatus,
                paymentStatus, handoffReason, createdAt, now, version + 1, resumeStatus, resumeId,
                resumeIdempotencyKey, resumeChecksum, resumeBoundarySequence, resumeDecisionAction,
                resumeDecisionMessage, resumeFailureCode, contractingUnitId, environmentLabel,
                environmentSourceMessageId, consentId);
    }

    public ReceptionConversation presentTerms(Instant now) {
        if (selectedService == null) {
            throw new IllegalStateException("service must be selected before terms");
        }
        if (mode != ReceptionMode.AI) {
            throw new IllegalStateException("human conversation cannot present terms");
        }
        return copy(mode, CommercialStage.TERMS, selectedService, TermsStatus.PRESENTED,
                paymentStatus, handoffReason, now);
    }

    /**
     * Reopens a legacy accepted conversation whose presentation evidence was
     * never recorded. Payment is reset so an old inferred acceptance can never
     * authorize a new charge without a fresh, auditable presentation.
     */
    public ReceptionConversation reopenTermsForAudit(Instant now) {
        if (termsStatus != TermsStatus.ACCEPTED || activeTermsConsentId != null) {
            throw new IllegalStateException("only an unaudited accepted conversation can reopen terms");
        }
        if (selectedService == null) {
            throw new IllegalStateException("service must be selected before terms");
        }
        if (mode != ReceptionMode.AI) {
            throw new IllegalStateException("human conversation cannot reopen terms");
        }
        return new ReceptionConversation(id, contactId, mode, CommercialStage.TERMS, selectedService,
                TermsStatus.PRESENTED, PaymentStatus.NOT_STARTED, handoffReason, createdAt,
                Objects.requireNonNull(now, "now"), version + 1, resumeStatus, resumeId,
                resumeIdempotencyKey, resumeChecksum, resumeBoundarySequence, resumeDecisionAction,
                resumeDecisionMessage, resumeFailureCode, contractingUnitId, environmentLabel,
                environmentSourceMessageId, null);
    }

    public ReceptionConversation acceptTerms(Instant now) {
        if (termsStatus != TermsStatus.PRESENTED) {
            throw new IllegalStateException("terms must be presented before acceptance");
        }
        return copy(mode, CommercialStage.PAYMENT, selectedService, TermsStatus.ACCEPTED,
                paymentStatus, handoffReason, now);
    }

    public ReceptionConversation declineTerms(Instant now) {
        return copy(mode, commercialStage, selectedService, TermsStatus.DECLINED,
                paymentStatus, handoffReason, now);
    }

    public ReceptionConversation preparePayment(boolean ignoredIcpComplete, String method, Instant now) {
        require(method, "method");
        if (!canPreparePayment(ignoredIcpComplete)) {
            throw new IllegalStateException("payment requires service and accepted terms");
        }
        return copy(mode, CommercialStage.PAYMENT, selectedService, termsStatus,
                PaymentStatus.PREPARED, handoffReason, now);
    }

    public ReceptionConversation receivePaymentProof(Instant now) {
        if (paymentStatus != PaymentStatus.PREPARED) {
            throw new IllegalStateException("payment must be prepared before proof");
        }
        return copy(mode, CommercialStage.PAYMENT, selectedService, termsStatus,
                PaymentStatus.PROOF_RECEIVED, handoffReason, now);
    }

    public ReceptionConversation confirmPayment(Instant now) {
        if (paymentStatus != PaymentStatus.PROOF_RECEIVED) {
            throw new IllegalStateException("only a received proof can be confirmed");
        }
        return copy(mode, CommercialStage.BRIEFING, selectedService, termsStatus,
                PaymentStatus.CONFIRMED, handoffReason, now);
    }

    public ReceptionConversation rejectPayment(Instant now) {
        if (paymentStatus != PaymentStatus.PROOF_RECEIVED) {
            throw new IllegalStateException("only a received proof can be rejected");
        }
        return copy(mode, CommercialStage.PAYMENT, selectedService, termsStatus,
                PaymentStatus.REJECTED, handoffReason, now);
    }

    public ReceptionConversation requestHumanHandoff(String reason, Instant now) {
        require(reason, "reason");
        if (mode == ReceptionMode.HUMAN) {
            return this;
        }
        return copy(ReceptionMode.HUMAN, commercialStage, selectedService, termsStatus,
                paymentStatus, reason, now, ResumeStatus.NONE, null, null, null, 0, null, null, null);
    }

    public ReceptionConversation beginResume(String nextResumeId, String idempotencyKey,
                                              String checksum, int boundarySequence, Instant now) {
        require(nextResumeId, "resumeId");
        require(idempotencyKey, "idempotencyKey");
        require(checksum, "resumeChecksum");
        if (mode != ReceptionMode.HUMAN) {
            throw new IllegalStateException("only human conversations can return to Urba");
        }
        if (resumeStatus != ResumeStatus.NONE && resumeStatus != ResumeStatus.FAILED_SAFE
                && resumeStatus != ResumeStatus.RETURNED_TO_HUMAN) {
            if (nextResumeId.equals(resumeId) && idempotencyKey.equals(resumeIdempotencyKey)) {
                return this;
            }
            throw new IllegalStateException("a resume is already in progress");
        }
        return copy(mode, commercialStage, selectedService, termsStatus, paymentStatus, handoffReason, now,
                ResumeStatus.SYNCHRONIZING, nextResumeId, idempotencyKey, checksum, boundarySequence,
                null, null, null);
    }

    public ReceptionConversation markResumeDeciding(Instant now) {
        if (resumeStatus != ResumeStatus.SYNCHRONIZING) {
            throw new IllegalStateException("resume is not synchronized");
        }
        return copy(mode, commercialStage, selectedService, termsStatus, paymentStatus, handoffReason, now,
                ResumeStatus.DECIDING, resumeId, resumeIdempotencyKey, resumeChecksum,
                resumeBoundarySequence, null, null, null);
    }

    public ReceptionConversation completeResume(String action, String message, Instant now) {
        if (resumeStatus != ResumeStatus.DECIDING && resumeStatus != ResumeStatus.SYNCHRONIZING) {
            throw new IllegalStateException("resume decision is not ready");
        }
        return copy(ReceptionMode.AI, commercialStage, selectedService, termsStatus, paymentStatus, null, now,
                ResumeStatus.COMPLETED, resumeId, resumeIdempotencyKey, resumeChecksum,
                resumeBoundarySequence, action, message, null);
    }

    public ReceptionConversation failResume(String failureCode, Instant now) {
        require(failureCode, "resumeFailureCode");
        return copy(ReceptionMode.HUMAN, commercialStage, selectedService, termsStatus, paymentStatus,
                handoffReason == null ? "retomada não concluída" : handoffReason, now,
                ResumeStatus.FAILED_SAFE, resumeId, resumeIdempotencyKey, resumeChecksum,
                resumeBoundarySequence, resumeDecisionAction, null, failureCode);
    }

    public ReceptionConversation returnResumeToHuman(String reason, Instant now) {
        return copy(ReceptionMode.HUMAN, commercialStage, selectedService, termsStatus, paymentStatus,
                reason, now, ResumeStatus.RETURNED_TO_HUMAN, resumeId, resumeIdempotencyKey,
                resumeChecksum, resumeBoundarySequence, "RETURN_TO_HUMAN", null, null);
    }

    private ReceptionConversation copy(ReceptionMode nextMode, CommercialStage nextStage,
                                       String nextService, TermsStatus nextTerms,
                                       PaymentStatus nextPayment, String nextReason, Instant now) {
        return copy(nextMode, nextStage, nextService, nextTerms, nextPayment, nextReason, now,
                resumeStatus, resumeId, resumeIdempotencyKey, resumeChecksum, resumeBoundarySequence,
                resumeDecisionAction, resumeDecisionMessage, resumeFailureCode);
    }

    private ReceptionConversation copy(ReceptionMode nextMode, CommercialStage nextStage,
                                       String nextService, TermsStatus nextTerms,
                                       PaymentStatus nextPayment, String nextReason, Instant now,
                                       ResumeStatus nextResumeStatus, String nextResumeId,
                                       String nextResumeKey, String nextChecksum, int nextBoundary,
                                       String nextAction, String nextMessage, String nextFailure) {
        return new ReceptionConversation(id, contactId, nextMode, nextStage, nextService,
                nextTerms, nextPayment, nextReason, createdAt, now, version + 1,
                nextResumeStatus, nextResumeId, nextResumeKey, nextChecksum, nextBoundary,
                nextAction, nextMessage, nextFailure, contractingUnitId, environmentLabel,
                environmentSourceMessageId, Objects.equals(nextService, selectedService) ? activeTermsConsentId : null);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
