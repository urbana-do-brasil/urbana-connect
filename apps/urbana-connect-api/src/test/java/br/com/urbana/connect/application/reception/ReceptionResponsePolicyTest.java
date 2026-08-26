package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceptionResponsePolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");
    private static final Instant CORRECTION_TIME = NOW.plusSeconds(10);

    private final ReceptionResponsePolicy policy = new ReceptionResponsePolicy();
    private final ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);

    @Test
    void considersAnExplicitCorrectionInTheFollowingResponseWithoutDeletingFactHistory() {
        CustomerFact original = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "ARQUITETA", "message-1", NOW);
        CustomerFact corrected = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "DESIGNER", "message-2", CORRECTION_TIME);
        CustomerFact superseded = original.supersede(corrected.id(), CORRECTION_TIME);
        List<CustomerFact> facts = List.of(superseded, corrected);

        assertThat(policy.currentFact(facts, "OCCUPATION", CORRECTION_TIME))
                .get()
                .extracting(CustomerFact::value)
                .isEqualTo("DESIGNER");

        AgentOutput responseUsingCorrection = new AgentOutput(
                "Entendi, vou considerar que você é designer. Qual ambiente deseja transformar?",
                AgentNextAction.AWAIT_CUSTOMER);
        ReceptionResponsePolicy.ValidationResult accepted = policy.validate(
                responseUsingCorrection, conversation, facts, CORRECTION_TIME);

        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.output()).isEqualTo(responseUsingCorrection);
        assertThat(facts).containsExactly(superseded, corrected);
        assertThat(superseded.supersededBy()).isEqualTo(corrected.id());
        assertThat(superseded.sourceMessageId()).isEqualTo("message-1");

        AgentOutput staleResponse = new AgentOutput(
                "Entendi, vou considerar que você é arquiteta. Qual ambiente deseja transformar?",
                AgentNextAction.AWAIT_CUSTOMER);
        ReceptionResponsePolicy.ValidationResult rejected = policy.validate(
                staleResponse, conversation, facts, CORRECTION_TIME);

        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.reason()).isEqualTo("stale_fact_reference");
        assertThat(rejected.output().nextAction()).isEqualTo(AgentNextAction.AWAIT_CUSTOMER);
        assertThat(rejected.output().message()).doesNotContainIgnoringCase("arquiteta");
    }

    @Test
    void doesNotExposeTentativeOrStaleFactsAsCurrentReusableProfileValues() {
        CustomerFact tentativePronoun = CustomerFact.tentative(
                "contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "message-tentative", NOW);
        CustomerFact oldOccupation = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "ARQUITETA", "message-old", NOW.minusSeconds(30));
        CustomerFact tentativeLatestOccupation = CustomerFact.tentative(
                "contact-1", "OCCUPATION", "DESIGNER", "message-latest", NOW.minusSeconds(10));
        CustomerFact staleOccupation = new CustomerFact(
                "occupation-stale", "contact-1", "OCCUPATION", "ARQUITETA",
                br.com.urbana.connect.domain.reception.model.FactConfidence.CONFIRMED,
                "message-stale", NOW.minusSeconds(30), NOW.minusSeconds(1), null);

        assertThat(policy.currentFact(List.of(tentativePronoun), "PRONOUN_PREFERENCE", NOW)).isEmpty();
        assertThat(policy.currentFact(List.of(staleOccupation), "OCCUPATION", NOW)).isEmpty();
        assertThat(policy.currentFact(
                List.of(oldOccupation, tentativeLatestOccupation), "OCCUPATION", NOW)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Temos o serviço de Paisagismo por R$ 99,00.",
            "O serviço Decor custa R$ 99,00.",
            "O projeto Decor fica pronto em 7 dias.",
            "Posso oferecer 20% de desconto no Decor.",
            "O Decor tem disponibilidade imediata.",
            "O Decor inclui consultoria extra sem custo."
    })
    void convertsInventedCommercialClaimsToAClarificationWithoutPublishingTheClaim(String inventedClaim) {
        AgentOutput candidate = new AgentOutput(inventedClaim, AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("unsupported_commercial_claim");
        assertThat(result.output().nextAction()).isEqualTo(AgentNextAction.AWAIT_CUSTOMER);
        assertThat(result.output().message()).doesNotContain("R$ 99")
                .doesNotContainIgnoringCase("paisagismo")
                .doesNotContainIgnoringCase("7 dias")
                .doesNotContainIgnoringCase("desconto")
                .doesNotContainIgnoringCase("disponibilidade")
                .doesNotContainIgnoringCase("consultoria extra");
    }

    @Test
    void keepsTheValidatedConversationalMessageAndNextActionAsSeparateFields() {
        AgentOutput candidate = new AgentOutput(
                "O Decor custa R$ 400,00. Qual ambiente você quer transformar?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output().message()).contains("R$ 400,00");
        assertThat(result.output().nextAction()).isEqualTo(AgentNextAction.AWAIT_CUSTOMER);
        assertThat(result.output().handoffReason()).isNull();
    }

    @Test
    void acceptsTheApprovedPriceForTheSpecificDecorVariantMentionedByHermes() {
        AgentOutput candidate = new AgentOutput(
                "Perfeito. Para a sala, a opção aprovada é Decor Pintura: renovação com pintura, "
                        + "sem quebra-quebra, por R$ 250,00. Deseja seguir com essa opção?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsTheSpecificServiceAndIcpQuestionReturnedByHermes() {
        AgentOutput candidate = new AgentOutput(
                "Entendi: você escolheu o serviço Decor Pintura para a sala. Para continuar, "
                        + "quais pronomes devo usar, é a primeira vez que você contrata esse tipo de serviço "
                        + "e qual é sua ocupação?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsAnApprovedServiceFollowedByARepeatHiringQuestion() {
        AgentOutput candidate = new AgentOutput(
                "Entendi: você escolheu o Decor Pintura para a sala. É uma renovação com pintura, "
                        + "sem quebra-quebra, por R$ 250,00. Você já contratou algum serviço parecido antes?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsAnApprovedServiceFollowedByTheFirstHiringQuestionReturnedByHermes() {
        AgentOutput candidate = new AgentOutput(
                "Certo: você escolheu Decor Pintura para a sala. É uma renovação com pintura, "
                        + "sem quebra-quebra, por R$ 250,00. Você está contratando esse tipo de serviço "
                        + "pela primeira vez?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsAnApprovedServiceFollowedByTheProviderHiringQuestionReturnedByHermes() {
        AgentOutput candidate = new AgentOutput(
                "Perfeito, registrei Decor Pintura para a sala. É uma renovação com pintura, "
                        + "sem quebra-quebra, por R$ 250,00. É a primeira vez que você contrata "
                        + "um serviço da Urbana?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsAnApprovedServiceFollowedByATypeHiringQuestionReturnedByHermes() {
        AgentOutput candidate = new AgentOutput(
                "Perfeito, registrei Decor Pintura para a sua sala. O serviço oferece renovação "
                        + "com pintura, sem quebra-quebra, por R$ 250,00. Você está contratando um "
                        + "serviço desse tipo pela primeira vez?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsTheSelectedServiceWordingReturnedByHermes() {
        AgentOutput candidate = new AgentOutput(
                "Entendi: para a sua sala, o serviço selecionado é Decor Pintura. Ele oferece renovação "
                        + "com pintura, sem quebra-quebra, por R$ 250,00. Você está contratando esse tipo "
                        + "de serviço pela primeira vez?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsTheChosenServiceWordingReturnedByHermes() {
        AgentOutput candidate = new AgentOutput(
                "Entendi: o serviço escolhido é Decor Pintura para a sala — renovação com pintura, "
                        + "sem quebra-quebra, por R$ 250,00. É a sua primeira contratação desse tipo de serviço?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsThePreparedPaymentPromptReturnedByHermes() {
        CommercialPolicyService commercial = new CommercialPolicyService();
        List<CustomerFact> facts = List.of(
                CustomerFact.confirmed("contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "pronoun", NOW),
                CustomerFact.confirmed("contact-1", "FIRST_TIME_HIRING", "YES", "first", NOW),
                CustomerFact.confirmed("contact-1", "OCCUPATION", "DESIGNER", "occupation", NOW));
        ReceptionConversation prepared = commercial.selectService(conversation, "DECOR", NOW);
        prepared = commercial.presentTerms(prepared, facts, NOW);
        prepared = commercial.acceptTerms(prepared, NOW);
        prepared = commercial.preparePayment(prepared, facts, "PIX", NOW);
        AgentOutput candidate = new AgentOutput(
                "O pagamento via PIX foi preparado: https://fixtures.urbana.local/payment/decor. "
                        + "Após realizar o pagamento, envie o comprovante; ele será submetido à aprovação humana.",
                AgentNextAction.AWAIT_PAYMENT_PROOF);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, prepared, facts, NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsAClarifyingQuestionThatUsesProjectAsAGenericDescriptor() {
        AgentOutput candidate = new AgentOutput(
                "Olá! Sou a Urba, assistente virtual da Urbana do Brasil. "
                        + "Você busca ajuda para escolher cores e móveis ou quer um projeto completo "
                        + "para a sala? Se puder, diga também o tamanho do ambiente e o estilo que você prefere.",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void acceptsAClarifyingQuestionThatUsesOptionAsAGenericDescriptor() {
        AgentOutput candidate = new AgentOutput(
                "Claro! Para eu indicar a opção certa, sua sala tem até 20 m²? "
                        + "Você busca apenas uma solução de decoração, renovação com pintura sem quebra-quebra "
                        + "ou uma reforma completa?",
                AgentNextAction.AWAIT_CUSTOMER);

        ReceptionResponsePolicy.ValidationResult result = policy.validate(
                candidate, conversation, List.of(), NOW);

        assertThat(result.accepted()).isTrue();
        assertThat(result.output()).isEqualTo(candidate);
    }

    @Test
    void rejectsAValidCatalogPriceWhenItIsAssociatedWithAnotherService() {
        List<String> crossServicePriceClaims = List.of(
                "O Decor Pintura custa R$ 400,00.",
                "O Decor custa R$ 250,00.");

        for (String claim : crossServicePriceClaims) {
            ReceptionResponsePolicy.ValidationResult result = policy.validate(
                    new AgentOutput(claim, AgentNextAction.AWAIT_CUSTOMER),
                    conversation, List.of(), NOW);

            assertThat(result.accepted()).as(claim).isFalse();
            assertThat(result.reason()).as(claim).isEqualTo("unsupported_commercial_claim");
            assertThat(result.output().nextAction()).as(claim).isEqualTo(AgentNextAction.AWAIT_CUSTOMER);
            assertThat(result.output().message()).as(claim).doesNotContain("R$");
        }
    }
}
