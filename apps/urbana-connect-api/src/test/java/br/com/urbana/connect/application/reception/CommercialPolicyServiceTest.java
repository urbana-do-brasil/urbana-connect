package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMode;
import br.com.urbana.connect.domain.reception.model.CommercialStage;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.domain.servicecatalog.model.AreaRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommercialPolicyServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void allowsTermsAndPaymentWithoutOptionalIcpAndKeepsHumanApprovalBeforeBriefing() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);

        assertThat(policy.mandatoryIcpFields())
                .containsExactly("PRONOUN_PREFERENCE", "FIRST_TIME_HIRING", "OCCUPATION");
        assertThat(policy.missingIcpFields(List.of(), NOW))
                .containsExactly("PRONOUN_PREFERENCE", "FIRST_TIME_HIRING", "OCCUPATION");
        assertThat(policy.isIcpComplete(List.of(), NOW)).isFalse();

        conversation = policy.selectService(conversation, "DECOR_INTERIORES", NOW);
        conversation = policy.presentTerms(conversation, List.of(), NOW);
        assertThat(conversation.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
        ReceptionConversation termsPresented = conversation;
        assertThatThrownBy(() -> policy.preparePayment(termsPresented, List.of(), "PIX", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accepted");

        assertThatThrownBy(() -> policy.acceptTerms(termsPresented, "ok", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clear");
        conversation = policy.acceptTerms(conversation, "aceito os termos", NOW);
        ReceptionConversation termsAccepted = conversation;
        assertThatThrownBy(() -> policy.preparePayment(termsAccepted, List.of(), "CRYPTO", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not approved");
        conversation = policy.preparePayment(conversation, List.of(), "PIX", NOW);
        assertThat(conversation.paymentStatus()).isEqualTo(PaymentStatus.PREPARED);
        conversation = policy.receivePaymentProof(conversation, NOW);
        assertThat(conversation.paymentStatus()).isEqualTo(PaymentStatus.PROOF_RECEIVED);
        ReceptionConversation proofReceived = conversation;
        assertThatThrownBy(() -> policy.briefingFor(proofReceived))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmed");

        conversation = policy.approvePaymentProof(conversation, NOW);
        assertThat(conversation.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(policy.briefingFor(conversation)).containsIgnoringCase("briefing").contains("DECOR_INTERIORES");
    }

    @Test
    void treatsRefusalAsAnsweredAndUsesOnlyTheLatestReusableIcpVersion() {
        CommercialPolicyService policy = new CommercialPolicyService();
        CustomerFact preference = CustomerFact.confirmed(
                "contact-1", "PRONOUN_PREFERENCE", CommercialPolicyService.PREFER_NOT_TO_ANSWER,
                "m-preference", NOW);
        CustomerFact firstHiring = CustomerFact.confirmed(
                "contact-1", "FIRST_TIME_HIRING", "NÃO INFORMADO", "m-first", NOW);
        CustomerFact oldOccupation = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "ARQUITETA", "m-old", NOW.minusSeconds(30));
        CustomerFact latestOccupation = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "DESIGNER", "m-latest", NOW.minusSeconds(10));
        CustomerFact supersededOccupation = oldOccupation.supersede(
                latestOccupation.id(), latestOccupation.validFrom());
        CustomerFact tentativeNeed = CustomerFact.tentative(
                "contact-1", "NEED", "sala", "m-need", NOW);

        assertThat(policy.missingIcpFields(
                List.of(preference, firstHiring, supersededOccupation, latestOccupation, tentativeNeed), NOW))
                .isEmpty();
        assertThat(policy.isIcpComplete(
                List.of(preference, firstHiring, supersededOccupation, latestOccupation, tentativeNeed), NOW))
                .isTrue();
    }

    @Test
    void tentativeLatestVersionDoesNotFallBackToAnOlderConfirmedIcpValue() {
        CommercialPolicyService policy = new CommercialPolicyService();
        CustomerFact oldOccupation = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "ARQUITETA", "m-old", NOW.minusSeconds(30));
        CustomerFact tentativeLatestOccupation = CustomerFact.tentative(
                "contact-1", "OCCUPATION", "DESIGNER", "m-tentative", NOW.minusSeconds(10));

        assertThat(policy.missingIcpFields(List.of(oldOccupation, tentativeLatestOccupation), NOW))
                .containsExactly("PRONOUN_PREFERENCE", "FIRST_TIME_HIRING", "OCCUPATION");
    }

    @Test
    void exposesExactlyTheFourRichCanonicalServicesWithoutLegacyDecorAlias() {
        CommercialPolicyService policy = new CommercialPolicyService();

        assertThat(policy.services())
                .hasSize(4)
                .extracting(CommercialPolicyService.ServiceFixture::serviceType)
                .containsExactly("DECOR_INTERIORES", "DECOR_PINTURA", "DECOR_FACHADA", "DECOR_REFORMA");
        assertThat(policy.services())
                .noneMatch(service -> "DECOR".equals(service.serviceType()));

        assertThat(policy.service("DECOR_INTERIORES"))
                .extracting(CommercialPolicyService.ServiceFixture::price,
                        CommercialPolicyService.ServiceFixture::areaRule)
                .containsExactly(new BigDecimal("400.00"), AreaRule.UP_TO_20_SQM_PER_ENVIRONMENT);
        assertThat(policy.service("DECOR_PINTURA"))
                .extracting(CommercialPolicyService.ServiceFixture::price,
                        CommercialPolicyService.ServiceFixture::areaRule)
                .containsExactly(new BigDecimal("250.00"), AreaRule.UNLIMITED_BY_CATALOG);
        assertThat(policy.service("DECOR_FACHADA"))
                .extracting(CommercialPolicyService.ServiceFixture::price,
                        CommercialPolicyService.ServiceFixture::areaRule)
                .containsExactly(new BigDecimal("350.00"), AreaRule.UNLIMITED_BY_CATALOG);
        assertThat(policy.service("DECOR_REFORMA"))
                .extracting(CommercialPolicyService.ServiceFixture::price,
                        CommercialPolicyService.ServiceFixture::areaRule)
                .containsExactly(new BigDecimal("450.00"), AreaRule.UP_TO_20_SQM_PER_ENVIRONMENT);

        policy.services().forEach(service -> {
            assertThat(service.deliverables())
                    .containsExactly("Manual PDF", "Tour Virtual", "3 opções de solução", "2 rodadas consolidadas");
            assertThat(service.process()).anyMatch(value -> value.equals("briefing"));
            assertThat(service.process()).anyMatch(value -> value.equals("medidas, fotos e vídeos"));
            assertThat(service.process()).anyMatch(value -> value.contains("Google Meet"));
            assertThat(service.process()).anyMatch(value -> value.equals("produção"));
            assertThat(service.process()).anyMatch(value -> value.contains("7 dias úteis"));
            assertThat(service.process()).anyMatch(value -> value.contains("e-mail"));
            assertThat(service.responsibilities()).isNotEmpty();
            assertThat(service.exclusions()).isNotEmpty();
            assertThat(service.support()).contains("3 meses").containsIgnoringCase("sem visita");
            assertThat(service.termsUrl()).startsWith("https://fixtures.urbana.local/");
            assertThat(service.paymentUrl()).startsWith("https://fixtures.urbana.local/");
            assertThat(service.briefingUrl()).startsWith("https://fixtures.urbana.local/");
            assertThat(service.briefingText()).contains(service.serviceType());
        });

        assertThat(policy.service("DECOR_INTERIORES").scope()).contains("layout");
        assertThat(policy.service("DECOR_PINTURA").scope())
                .contains("pintura", "desenhos", "tintas")
                .doesNotContain("20 m²");
        assertThat(policy.service("DECOR_FACHADA").scope()).containsIgnoringCase("fachada").contains("externa");
        assertThat(policy.service("DECOR_REFORMA").scope()).containsIgnoringCase("reforma");
    }

    @Test
    void appliesAreaRulesOnlyToInteriorsAndReformaAndRoutesExcessToArchitect() {
        CommercialPolicyService policy = new CommercialPolicyService();

        assertThat(policy.isAreaWithinCatalog("DECOR_INTERIORES", new BigDecimal("20"))).isTrue();
        assertThat(policy.isAreaWithinCatalog("DECOR_INTERIORES", new BigDecimal("20.01"))).isFalse();
        assertThat(policy.requiresArchitectAreaReview("DECOR_INTERIORES", new BigDecimal("20.01"))).isTrue();
        assertThat(policy.isAreaWithinCatalog("DECOR_REFORMA", new BigDecimal("20"))).isTrue();
        assertThat(policy.isAreaWithinCatalog("DECOR_REFORMA", new BigDecimal("20.01"))).isFalse();
        assertThat(policy.requiresArchitectAreaReview("DECOR_REFORMA", new BigDecimal("21"))).isTrue();

        assertThat(policy.isAreaWithinCatalog("DECOR_PINTURA", new BigDecimal("1000"))).isTrue();
        assertThat(policy.requiresArchitectAreaReview("DECOR_PINTURA", new BigDecimal("1000"))).isFalse();
        assertThat(policy.isAreaWithinCatalog("DECOR_FACHADA", new BigDecimal("1000"))).isTrue();
        assertThat(policy.requiresArchitectAreaReview("DECOR_FACHADA", new BigDecimal("1000"))).isFalse();
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
                "Segue o briefing DECOR: fixture local.", AgentNextAction.AWAIT_CUSTOMER), conversation))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.reconcileOutput(new AgentOutput(
                "Pagamento confirmado.", AgentNextAction.NONE), conversation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotTreatNegativePaymentWordingAsConfirmation() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);
        AgentOutput candidate = new AgentOutput(
                "Nenhum pagamento foi realizado ou confirmado.", AgentNextAction.AWAIT_CUSTOMER);

        assertThat(policy.reconcileOutput(candidate, conversation)).isEqualTo(candidate);
    }

    @Test
    void onlyAllowsWaitingForPaymentApprovalAfterProofWasReceived() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = new ReceptionConversation("conversation-1", "contact-1",
                ReceptionMode.AI, CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED,
                PaymentStatus.NOT_STARTED, null, NOW, NOW, 1);

        assertThatThrownBy(() -> policy.reconcileOutput(new AgentOutput(
                "Vou aguardar a confirmação do pagamento.", AgentNextAction.AWAIT_PAYMENT_APPROVAL), conversation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proof");
    }

    @Test
    void allowsExplainingThatBriefingComesAfterPaymentWithoutClaimingItsRelease() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);
        AgentOutput explanation = new AgentOutput(
                "O briefing faz parte do processo e será enviado após a confirmação do pagamento.",
                AgentNextAction.AWAIT_CUSTOMER);

        assertThat(policy.reconcileOutput(explanation, conversation)).isEqualTo(explanation);
    }

    @Test
    void allowsRichServiceExplanationThatMentionsBriefingAndFutureDeliveryWithoutClaimingRelease() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);
        AgentOutput explanation = new AgentOutput(
                "A consultoria inclui briefing, análise de medidas, fotos e vídeos, reunião pelo Google Meet, "
                        + "Manual em PDF e Tour Virtual. A entrega final é enviada por e-mail.",
                AgentNextAction.AWAIT_CUSTOMER);

        assertThat(policy.reconcileOutput(explanation, conversation)).isEqualTo(explanation);
    }

    @Test
    void rejectsPrematurePaymentLinkAndBriefingReadyClaims() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);

        assertThatThrownBy(() -> policy.reconcileOutput(new AgentOutput(
                "Para pagar, acesse https://fixtures.urbana.local/payment/decor.",
                AgentNextAction.AWAIT_CUSTOMER), conversation))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.reconcileOutput(new AgentOutput(
                "Seu briefing está pronto para preencher.", AgentNextAction.AWAIT_CUSTOMER), conversation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotTreatAnUnrelatedApprovalInTheServiceExplanationAsPaymentConfirmation() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);
        AgentOutput explanation = new AgentOutput(
                "O pagamento é antecipado e o prazo pode pausar enquanto houver pendência de feedback ou aprovação.",
                AgentNextAction.AWAIT_CUSTOMER);

        assertThat(policy.reconcileOutput(explanation, conversation)).isEqualTo(explanation);
    }

    @Test
    void acceptsClearTermsWithNaturalQualifiersButNotBareAcceptance() {
        CommercialPolicyService policy = new CommercialPolicyService();

        assertThat(policy.isExplicitTermsAcceptance(
                "Aceito claramente os termos apresentados e quero seguir com a contratação.")).isTrue();
        assertThat(policy.isExplicitTermsAcceptance("Aceito")).isFalse();
        assertThat(policy.isExplicitTermsAcceptance("Não aceito os termos.")).isFalse();
    }

    @Test
    void resolvesOnlyApprovedInteractiveServiceReplyIds() {
        CommercialPolicyService policy = new CommercialPolicyService();

        assertThat(policy.serviceTypeForInteractiveReply("service.decor")).isEqualTo("DECOR_INTERIORES");
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
    void rejectsHumanApprovalWaitReturnedByHermesBeforeProof() {
        CommercialPolicyService policy = new CommercialPolicyService();
        ReceptionConversation conversation = new ReceptionConversation("conversation-1", "contact-1",
                ReceptionMode.AI, CommercialStage.PAYMENT, "DECOR", TermsStatus.ACCEPTED,
                PaymentStatus.PREPARED, null, NOW, NOW, 1);
        AgentOutput candidate = new AgentOutput(
                "Comprovante recebido. A aprovação do pagamento será feita exclusivamente por uma pessoa; "
                        + "ainda não posso confirmar a aprovação.",
                AgentNextAction.AWAIT_PAYMENT_APPROVAL);

        assertThatThrownBy(() -> policy.reconcileOutput(candidate, conversation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proof");
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
