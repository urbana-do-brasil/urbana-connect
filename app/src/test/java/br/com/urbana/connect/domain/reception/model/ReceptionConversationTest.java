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
    void handoffStopsAiAndReturnRequiresExplicitOperatorAction() {
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", now)
                .requestHumanHandoff("cliente pediu uma pessoa", now.plusSeconds(1));

        assertThat(conversation.isHuman()).isTrue();
        assertThat(conversation.handoffReason()).isEqualTo("cliente pediu uma pessoa");
        assertThatIllegalStateException().isThrownBy(() -> conversation.presentTerms(now.plusSeconds(2)));

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
