package br.com.urbana.connect.domain.reception.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ReceptionConversationTest {

    private final Instant now = Instant.parse("2026-08-04T12:00:00Z");

    @Test
    void startsInAiDiscoveryWithSafeCommercialBarriers() {
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", now);

        assertThat(conversation.mode()).isEqualTo(ReceptionMode.AI);
        assertThat(conversation.commercialStage()).isEqualTo(CommercialStage.DISCOVERY);
        assertThat(conversation.termsStatus()).isEqualTo(TermsStatus.NOT_PRESENTED);
        assertThat(conversation.paymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(conversation.version()).isZero();
    }

    @Test
    void rejectsHumanModeWithoutReason() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReceptionConversation(
                "conversation-1", "contact-1", ReceptionMode.HUMAN, CommercialStage.DISCOVERY,
                null, TermsStatus.NOT_PRESENTED, PaymentStatus.NOT_STARTED, null, now, now, 0));
    }

    @Test
    void paymentCannotBypassIcpAndTerms() {
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", now).selectService("DECOR", now);

        ReceptionConversation beforeTerms = conversation;
        assertThatIllegalStateException().isThrownBy(() -> beforeTerms.preparePayment(true, "PIX", now));
        ReceptionConversation afterPresenting = conversation.presentTerms(now);
        assertThatIllegalStateException().isThrownBy(() -> afterPresenting.preparePayment(true, "PIX", now));
    }

    @Test
    void incompleteIcpDoesNotBlockPaymentAfterTermsAreAccepted() {
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", now)
                .selectService("DECOR_INTERIORES", now)
                .presentTerms(now)
                .acceptTerms(now);

        ReceptionConversation prepared = conversation.preparePayment(false, "PIX", now);

        assertThat(prepared.paymentStatus()).isEqualTo(PaymentStatus.PREPARED);
    }

    @Test
    void handoffStopsAiAndReturnRequiresExplicitOperatorAction() {
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", now)
                .requestHumanHandoff("cliente pediu uma pessoa", now.plusSeconds(1));

        assertThat(conversation.isHuman()).isTrue();
        assertThat(conversation.handoffReason()).isEqualTo("cliente pediu uma pessoa");
        assertThatIllegalStateException().isThrownBy(() -> conversation.presentTerms(now.plusSeconds(2)));

    }

    @Test
    void modelsTheVersionedFailClosedResumeStateAndOwnershipTransition() {
        ReceptionConversation human = ReceptionConversation.start("contact-1", now)
                .requestHumanHandoff("cliente pediu uma pessoa", now.plusSeconds(1));

        ReceptionConversation syncing = human.beginResume("resume-1", "return-1", "sha256:abc", 3,
                now.plusSeconds(2));
        ReceptionConversation deciding = syncing.markResumeDeciding(now.plusSeconds(3));
        ReceptionConversation completed = deciding.completeResume("WAIT", null, now.plusSeconds(4));

        assertThat(syncing.mode()).isEqualTo(ReceptionMode.HUMAN);
        assertThat(syncing.resumeStatus()).isEqualTo(ResumeStatus.SYNCHRONIZING);
        assertThat(deciding.resumeStatus()).isEqualTo(ResumeStatus.DECIDING);
        assertThat(completed.mode()).isEqualTo(ReceptionMode.AI);
        assertThat(completed.resumeStatus()).isEqualTo(ResumeStatus.COMPLETED);
        assertThat(completed.resumeId()).isEqualTo("resume-1");
        assertThat(completed.version()).isEqualTo(human.version() + 3);
    }

    @Test
    void preservesHumanOwnershipWhenResumeFailsAndReplaysSameActiveRequest() {
        ReceptionConversation human = ReceptionConversation.start("contact-1", now)
                .requestHumanHandoff("cliente pediu uma pessoa", now.plusSeconds(1));
        ReceptionConversation syncing = human.beginResume("resume-1", "return-1", "sha256:abc", 1,
                now.plusSeconds(2));

        assertThat(syncing.beginResume("resume-1", "return-1", "sha256:abc", 1, now.plusSeconds(3)))
                .isSameAs(syncing);
        ReceptionConversation failed = syncing.failResume("RESUME_UNAVAILABLE", now.plusSeconds(4));

        assertThat(failed.mode()).isEqualTo(ReceptionMode.HUMAN);
        assertThat(failed.resumeStatus()).isEqualTo(ResumeStatus.FAILED_SAFE);
        assertThat(failed.resumeFailureCode()).isEqualTo("RESUME_UNAVAILABLE");
    }

    @Test
    void changingServiceInvalidatesTermsAndPaymentPreparedForThePreviousService() {
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", now)
                .selectService("DECOR", now)
                .presentTerms(now)
                .acceptTerms(now)
                .preparePayment(true, "PIX", now);

        ReceptionConversation changed = conversation.selectService("ARCHITECTURE", now.plusSeconds(1));

        assertThat(changed.selectedService()).isEqualTo("ARCHITECTURE");
        assertThat(changed.termsStatus()).isEqualTo(TermsStatus.NOT_PRESENTED);
        assertThat(changed.paymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(changed.commercialStage()).isEqualTo(CommercialStage.ICP);
    }
}
