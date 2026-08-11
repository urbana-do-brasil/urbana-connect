package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.CommercialStage;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommercialPolicyServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void enforcesIcpBeforeTermsThenTermsBeforePaymentAndHumanApprovalBeforeBriefing() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);
        List<CustomerFact> facts = List.of(
                CustomerFact.confirmed("contact-1", "PRONOUN_PREFERENCE", "PREFER_NOT_TO_ANSWER", "m-1", NOW),
                CustomerFact.confirmed("contact-1", "FIRST_TIME_HIRING", "YES", "m-2", NOW),
                CustomerFact.confirmed("contact-1", "OCCUPATION", "MICROEMPREENDEDOR", "m-3", NOW));

        assertThat(policy.isIcpComplete(facts, NOW)).isTrue();
        ReceptionConversation incomplete = conversation.selectService("DECOR", NOW);
        assertThatThrownBy(() -> policy.presentTerms(incomplete, facts.subList(0, 2), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ICP");

        conversation = policy.selectService(conversation, "DECOR", NOW);
        conversation = policy.presentTerms(conversation, facts, NOW);
        assertThat(conversation.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
        ReceptionConversation termsPresented = conversation;
        assertThatThrownBy(() -> policy.preparePayment(termsPresented, facts, "PIX", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accepted");

        conversation = policy.acceptTerms(conversation, NOW);
        ReceptionConversation termsAccepted = conversation;
        assertThatThrownBy(() -> policy.preparePayment(termsAccepted, facts, "CRYPTO", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not approved");
        conversation = policy.preparePayment(conversation, facts, "PIX", NOW);
        assertThat(conversation.paymentStatus()).isEqualTo(PaymentStatus.PREPARED);
        conversation = policy.receivePaymentProof(conversation, NOW);
        assertThat(conversation.paymentStatus()).isEqualTo(PaymentStatus.PROOF_RECEIVED);
        ReceptionConversation proofReceived = conversation;
        assertThatThrownBy(() -> policy.briefingFor(proofReceived))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmed");

        conversation = policy.approvePaymentProof(conversation, NOW);
        assertThat(conversation.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(policy.briefingFor(conversation)).containsIgnoringCase("briefing").contains("DECOR");
    }

    @Test
    void rejectsUnknownServiceAndDoesNotAllowModelToPublishBriefingBeforeApproval() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);
        assertThatThrownBy(() -> policy.selectService(conversation, "UNKNOWN", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catalog");

        AgentOutput requestedBriefing = new AgentOutput("aqui está o briefing", AgentNextAction.AWAIT_CUSTOMER);
        assertThatThrownBy(() -> policy.reconcileOutput(requestedBriefing, conversation))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.reconcileOutput(new AgentOutput(
                "Briefing DECOR: fixture local.", AgentNextAction.AWAIT_CUSTOMER), conversation))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.reconcileOutput(new AgentOutput(
                "Pagamento confirmado.", AgentNextAction.NONE), conversation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvesOnlyApprovedInteractiveServiceReplyIds() {
        CommercialPolicyService policy = new CommercialPolicyService();

        assertThat(policy.serviceTypeForInteractiveReply("service.decor")).isEqualTo("DECOR");
        assertThat(policy.serviceTypeForInteractiveReply("service.decor_reforma")).isEqualTo("DECOR_REFORMA");
        assertThatThrownBy(() -> policy.serviceTypeForInteractiveReply("service.paisagismo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catalog");
    }

    @Test
    void replacesEveryAgentMessageAfterProofWithCanonicalHumanApprovalStatus() {
        CommercialPolicyService policy = new CommercialPolicyService();
        List<CustomerFact> facts = List.of(
                CustomerFact.confirmed("contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "m-1", NOW),
                CustomerFact.confirmed("contact-1", "FIRST_TIME_HIRING", "YES", "m-2", NOW),
                CustomerFact.confirmed("contact-1", "OCCUPATION", "DESIGNER", "m-3", NOW));
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);
        conversation = policy.presentTerms(policy.selectService(conversation, "DECOR", NOW), facts, NOW);
        conversation = policy.preparePayment(policy.acceptTerms(conversation, NOW), facts, "PIX", NOW);
        conversation = policy.receivePaymentProof(conversation, NOW);

        AgentOutput reconciled = policy.reconcileOutput(
                new AgentOutput("Segue o briefing. Pagamento confirmado!", AgentNextAction.NONE), conversation);

        assertThat(reconciled.nextAction()).isEqualTo(AgentNextAction.AWAIT_PAYMENT_APPROVAL);
        assertThat(reconciled.message()).contains("aguarda validação humana").doesNotContainIgnoringCase("briefing");
    }

    @Test
    void allowsAStatementThatExplicitlySaysPaymentApprovalHasNotHappened() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = new ReceptionConversation("conversation-1", "contact-1",
                ReceptionMode.AI, CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED,
                PaymentStatus.PREPARED, null, NOW, NOW, 1);
        AgentOutput candidate = new AgentOutput(
                "O pagamento via PIX está preparado. Após realizar o pagamento, envie o comprovante. "
                        + "O comprovante não confirma a aprovação.", AgentNextAction.AWAIT_PAYMENT_PROOF);

        assertThat(policy.reconcileOutput(candidate, conversation)).isEqualTo(candidate);
    }

    @Test
    void allowsPendingHumanApprovalWordingInThePaymentProofInstructions() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = new ReceptionConversation("conversation-1", "contact-1",
                ReceptionMode.AI, CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED,
                PaymentStatus.PREPARED, null, NOW, NOW, 1);
        AgentOutput candidate = new AgentOutput(
                "O pagamento via PIX foi preparado. Envie o comprovante; ele será analisado e aprovado "
                        + "exclusivamente pela equipe humana.", AgentNextAction.AWAIT_PAYMENT_PROOF);

        assertThat(policy.reconcileOutput(candidate, conversation)).isEqualTo(candidate);
    }

    @Test
    void allowsProofSubmissionForHumanApprovalInThePaymentPromptReturnedByHermes() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = new ReceptionConversation("conversation-1", "contact-1",
                ReceptionMode.AI, CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED,
                PaymentStatus.PREPARED, null, NOW, NOW, 1);
        AgentOutput candidate = new AgentOutput(
                "O pagamento via PIX foi preparado: https://fixtures.urbana.local/payment/decor. "
                        + "Após realizar o pagamento, envie o comprovante; ele será submetido à aprovação humana.",
                AgentNextAction.AWAIT_PAYMENT_PROOF);

        assertThat(policy.reconcileOutput(candidate, conversation)).isEqualTo(candidate);
    }

    @Test
    void allowsFutureHumanApprovalWordingReturnedByHermes() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = new ReceptionConversation("conversation-1", "contact-1",
                ReceptionMode.AI, CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED,
                PaymentStatus.PREPARED, null, NOW, NOW, 1);
        AgentOutput candidate = new AgentOutput(
                "Comprovante recebido. A aprovação do pagamento será feita exclusivamente por uma pessoa; "
                        + "ainda não posso confirmar a aprovação.",
                AgentNextAction.AWAIT_PAYMENT_APPROVAL);

        assertThat(policy.reconcileOutput(candidate, conversation)).isEqualTo(candidate);
    }

    @Test
    void allowsTeamApprovalWordingReturnedByHermes() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = new ReceptionConversation("conversation-1", "contact-1",
                ReceptionMode.AI, CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED,
                PaymentStatus.PREPARED, null, NOW, NOW, 1);
        AgentOutput candidate = new AgentOutput(
                "Certo. O pagamento via Pix está preparado em https://fixtures.urbana.local/payment/decor. "
                        + "Após pagar, envie o comprovante; a aprovação será feita exclusivamente pela equipe da Urbana.",
                AgentNextAction.AWAIT_PAYMENT_PROOF);

        assertThat(policy.reconcileOutput(candidate, conversation)).isEqualTo(candidate);
    }

    @Test
    void allowsApprovalDependsOnHumanWordingReturnedByHermes() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = new ReceptionConversation("conversation-1", "contact-1",
                ReceptionMode.AI, CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED,
                PaymentStatus.PREPARED, null, NOW, NOW, 1);
        AgentOutput candidate = new AgentOutput(
                "O pagamento via PIX foi preparado. Após realizar o pagamento, envie o comprovante. "
                        + "A confirmação depende de aprovação humana.", AgentNextAction.AWAIT_PAYMENT_PROOF);

        assertThat(policy.reconcileOutput(candidate, conversation)).isEqualTo(candidate);
    }

    @Test
    void keepsImageOrDocumentProofAtEvidenceReceivedUntilBackendApproval() {
        CommercialPolicyService policy = new CommercialPolicyService();
        List<CustomerFact> facts = List.of(
                CustomerFact.confirmed("contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "m-1", NOW),
                CustomerFact.confirmed("contact-1", "FIRST_TIME_HIRING", "YES", "m-2", NOW),
                CustomerFact.confirmed("contact-1", "OCCUPATION", "DESIGNER", "m-3", NOW));
        ReceptionConversation prepared = policy.preparePayment(
                policy.acceptTerms(policy.presentTerms(
                        policy.selectService(ReceptionConversation.start("contact-1", NOW), "DECOR", NOW),
                        facts, NOW), NOW), facts, "PIX", NOW);

        ReceptionConversation evidence = policy.receivePaymentEvidence(prepared, NOW.plusSeconds(1));

        assertThat(evidence.paymentStatus()).isEqualTo(PaymentStatus.PROOF_RECEIVED);
        assertThat(evidence.paymentStatus()).isNotEqualTo(PaymentStatus.CONFIRMED);
        assertThatThrownBy(() -> policy.briefingFor(evidence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmed");
    }
}
