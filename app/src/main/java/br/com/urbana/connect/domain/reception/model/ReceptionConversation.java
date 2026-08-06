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
        long version) {

    public ReceptionConversation {
        id = require(id, "id");
        contactId = require(contactId, "contactId");
        mode = Objects.requireNonNull(mode, "mode");
        commercialStage = Objects.requireNonNull(commercialStage, "commercialStage");
        termsStatus = Objects.requireNonNull(termsStatus, "termsStatus");
        paymentStatus = Objects.requireNonNull(paymentStatus, "paymentStatus");
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

    public boolean canPreparePayment(boolean icpComplete) {
        return mode == ReceptionMode.AI && icpComplete && selectedService != null
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

    public ReceptionConversation preparePayment(boolean icpComplete, String method, Instant now) {
        require(method, "method");
        if (!canPreparePayment(icpComplete)) {
            throw new IllegalStateException("payment requires complete ICP, service and accepted terms");
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
                paymentStatus, reason, now);
    }

    private ReceptionConversation copy(ReceptionMode nextMode, CommercialStage nextStage,
                                       String nextService, TermsStatus nextTerms,
                                       PaymentStatus nextPayment, String nextReason, Instant now) {
        return new ReceptionConversation(id, contactId, nextMode, nextStage, nextService,
                nextTerms, nextPayment, nextReason, createdAt, now, version + 1);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
