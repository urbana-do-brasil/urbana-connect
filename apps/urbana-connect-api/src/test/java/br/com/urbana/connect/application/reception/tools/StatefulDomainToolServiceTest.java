package br.com.urbana.connect.application.reception.tools;

import br.com.urbana.connect.application.reception.CommercialPolicyService;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.model.FactConfidence;
import br.com.urbana.connect.domain.reception.model.IcpObservationEvent;
import br.com.urbana.connect.domain.reception.model.HumanHandoffNotification;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.domain.reception.model.PaymentStatus;
import br.com.urbana.connect.domain.reception.model.TermsConsentAudit;
import br.com.urbana.connect.domain.reception.model.TermsConsentStatus;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.IcpObservationEventGateway;
import br.com.urbana.connect.domain.reception.port.out.HumanHandoffNotificationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class StatefulDomainToolServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void neverConfirmsAClaimThatIsNotSupportedByTheLeaseBoundInboundMessage() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW)
                .bindContractingUnit("unit-1", "sala", "message-environment", NOW);
        transcript.messages.put("message-1", message("message-1", "Sou designer"));
        transcript.messages.put("message-2", message("message-2", "Oi"));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        Map<String, Object> supported = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "OCCUPATION", "value", "DESIGNER", "confidence", "CONFIRMED"),
                context("message-1"));
        Map<String, Object> forged = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "OCCUPATION", "value", "ARQUITETO", "confidence", "CONFIRMED"),
                context("message-2"));

        assertThat(supported).containsEntry("confidence", "CONFIRMED");
        assertThat(forged).containsEntry("confidence", "TENTATIVE");
        assertThat(facts.values).extracting(CustomerFact::confidence)
                .containsExactly(FactConfidence.CONFIRMED, FactConfidence.TENTATIVE);
        assertThat(facts.values.get(0).sourceMessageId()).isEqualTo("message-1");
    }

    @Test
    void bindsEnvironmentToTheBatchMessageThatActuallySupportsIt() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("conversation-1", "contact-1", NOW);
        transcript.messages.put("environment-event", message("environment-event", "Quero contratar para a sala de estar."));
        transcript.messages.put("occupation-event", message("occupation-event", "Sou designer."));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "ENVIRONMENT", "value", "sala de estar", "confidence", "CONFIRMED"),
                context("occupation-event", List.of("environment-event", "occupation-event")));

        assertThat(facts.values).singleElement().satisfies(fact -> {
            assertThat(fact.type()).isEqualTo("ENVIRONMENT");
            assertThat(fact.sourceMessageId()).isEqualTo("environment-event");
            assertThat(fact.confidence()).isEqualTo(FactConfidence.CONFIRMED);
        });
        assertThat(conversations.value.environmentSourceMessageId()).isEqualTo("environment-event");
    }

    @Test
    void neverConfirmsOrBindsANotInformedEnvironmentSentinel() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("conversation-1", "contact-1", NOW);
        transcript.messages.put("environment-refusal",
                message("environment-refusal", "Prefiro não informar o ambiente."));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        Map<String, Object> recorded = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "ENVIRONMENT", "value", "NÃO INFORMADO", "confidence", "CONFIRMED"),
                context("environment-refusal"));

        assertThat(recorded).containsEntry("value", "NÃO INFORMADO")
                .containsEntry("confidence", "TENTATIVE");
        assertThat(conversations.value.contractingUnitId()).isNull();
        assertThat(conversations.value.environmentLabel()).isNull();
    }

    @Test
    void neverConfirmsOrBindsAnEnvironmentFromAnArbitrarySubstring() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("conversation-1", "contact-1", NOW);
        transcript.messages.put("environment-message", message("environment-message", "Quero decorar a sala."));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        Map<String, Object> recorded = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "ENVIRONMENT", "value", "ala", "confidence", "CONFIRMED"),
                context("environment-message"));

        assertThat(recorded).containsEntry("confidence", "TENTATIVE");
        assertThat(conversations.value.contractingUnitId()).isNull();
        assertThat(conversations.value.environmentLabel()).isNull();
    }

    @Test
    void neverConfirmsNegatedOccupationOrFirstTimeClaims() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        transcript.messages.put("message-occupation", message("message-occupation", "Não sou designer"));
        transcript.messages.put("message-first-time", message("message-first-time", "Não é a primeira vez"));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        Map<String, Object> occupation = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "OCCUPATION", "value", "DESIGNER", "confidence", "CONFIRMED"),
                context("message-occupation"));
        Map<String, Object> firstTime = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "FIRST_TIME_HIRING", "value", "YES", "confidence", "CONFIRMED"),
                context("message-first-time"));

        assertThat(occupation).containsEntry("confidence", "TENTATIVE");
        assertThat(firstTime).containsEntry("confidence", "TENTATIVE");
    }

    @Test
    void supersedesThePreviousFactWithTheRealIdOfTheReplacementAndKeepsCurrentReadsConsistent() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        transcript.messages.put("message-occupation-1", message("message-occupation-1", "Sou designer."));
        transcript.messages.put("message-occupation-2", message("message-occupation-2", "Sou arquiteto."));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "OCCUPATION", "value", "DESIGNER", "confidence", "CONFIRMED"),
                context("message-occupation-1"));
        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "OCCUPATION", "value", "ARQUITETO", "confidence", "CONFIRMED"),
                context("message-occupation-2"));

        CustomerFact previous = facts.values.stream()
                .filter(fact -> "DESIGNER".equals(fact.value())).findFirst().orElseThrow();
        CustomerFact replacement = facts.values.stream()
                .filter(fact -> "ARQUITETO".equals(fact.value())).findFirst().orElseThrow();
        assertThat(previous.supersededBy()).isEqualTo(replacement.id());
        assertThat(facts.findCurrentByContactId("contact-1", NOW))
                .containsExactly(replacement)
                .allSatisfy(fact -> assertThat(fact.supersededBy()).isNull());
    }

    @Test
    void normalizesCommonModelLabelsToTheCanonicalFactTypes() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        transcript.messages.put("message-pronoun", message("message-pronoun", "Meu pronome é ela/dela."));
        transcript.messages.put("message-occupation", message("message-occupation", "Sou designer."));

        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "PRONOMES", "value", "ela/dela", "confidence", "CONFIRMED"),
                context("message-pronoun"));
        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "PROFISSÃO", "value", "designer", "confidence", "CONFIRMED"),
                context("message-occupation"));

        assertThat(facts.values).extracting(CustomerFact::type)
                .containsExactly("PRONOUN_PREFERENCE", "OCCUPATION");
        assertThat(facts.values).extracting(CustomerFact::value)
                .containsExactly("ela/dela", "designer");
    }

    @Test
    void canonicalizesNaturalModelFactValuesBeforePersistenceAndPolicyChecks() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        transcript.messages.put("message-pronoun", message("message-pronoun", "Meu pronome é ela/dela."));
        transcript.messages.put("message-first-time", message("message-first-time",
                "É a minha primeira vez contratando design."));
        transcript.messages.put("message-occupation", message("message-occupation", "Sou designer."));
        transcript.messages.put("message-service", message("message-service", "Quero contratar Decor."));

        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "PRONOMES", "value", "ela/dela", "confidence", "CONFIRMED"),
                context("message-pronoun"));
        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "FIRST TIME", "value", "true", "confidence", "CONFIRMED"),
                context("message-first-time"));
        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "PROFISSÃO", "value", "designer", "confidence", "CONFIRMED"),
                context("message-occupation"));
        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "SERVICE", "value", "Decor", "confidence", "CONFIRMED"),
                context("message-service"));

        assertThat(facts.values).extracting(CustomerFact::value)
                .containsExactly("ela/dela", "SIM", "designer", "DECOR_INTERIORES");
        assertThat(facts.values).extracting(CustomerFact::confidence)
                .containsOnly(FactConfidence.CONFIRMED);
        assertThat(conversations.value.selectedService()).isEqualTo("DECOR_INTERIORES");
    }

    @Test
    void persistsTheApprovedFirstHiringValuesAsSimNaoAndNotInformed() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        transcript.messages.put("message-first-yes", message("message-first-yes", "Sim, é a primeira vez."));
        transcript.messages.put("message-first-no", message("message-first-no", "Não, já contratei antes."));
        transcript.messages.put("message-first-unknown", message("message-first-unknown", "Prefiro não informar."));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "FIRST_TIME_HIRING", "value", "YES", "confidence", "CONFIRMED"),
                context("message-first-yes"));
        assertThat(facts.findCurrentByContactId("contact-1", NOW)).singleElement()
                .extracting(CustomerFact::value).isEqualTo("SIM");

        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "FIRST_TIME_HIRING", "value", "NO", "confidence", "CONFIRMED"),
                context("message-first-no"));
        assertThat(facts.findCurrentByContactId("contact-1", NOW)).singleElement()
                .extracting(CustomerFact::value).isEqualTo("NÃO");

        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "FIRST_TIME_HIRING", "value", "NÃO INFORMADO", "confidence", "CONFIRMED"),
                context("message-first-unknown"));
        assertThat(facts.findCurrentByContactId("contact-1", NOW)).singleElement()
                .extracting(CustomerFact::value).isEqualTo("NÃO INFORMADO");
    }

    @Test
    void acceptsLegacyBooleanAndPortugueseFirstHiringInputs() {
        for (String input : List.of("YES", "true", "sim")) {
            MemoryConversation conversations = new MemoryConversation();
            MemoryFacts facts = new MemoryFacts();
            MemoryTranscript transcript = new MemoryTranscript();
            conversations.value = ReceptionConversation.start("contact-1", NOW);
            transcript.messages.put("message-yes-" + input,
                    message("message-yes-" + input, "Sim, é a primeira vez."));
            StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                    conversations, facts, transcript);

            tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                    Map.of("factType", "FIRST_TIME_HIRING", "value", input, "confidence", "CONFIRMED"),
                    context("message-yes-" + input));

            assertThat(facts.values).singleElement().extracting(CustomerFact::value).isEqualTo("SIM");
        }
        for (String input : List.of("NO", "false", "não")) {
            MemoryConversation conversations = new MemoryConversation();
            MemoryFacts facts = new MemoryFacts();
            MemoryTranscript transcript = new MemoryTranscript();
            conversations.value = ReceptionConversation.start("contact-1", NOW);
            transcript.messages.put("message-no-" + input,
                    message("message-no-" + input, "Não, já contratei antes."));
            StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                    conversations, facts, transcript);

            tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                    Map.of("factType", "FIRST_TIME_HIRING", "value", input, "confidence", "CONFIRMED"),
                    context("message-no-" + input));

            assertThat(facts.values).singleElement().extracting(CustomerFact::value).isEqualTo("NÃO");
        }
    }

    @Test
    void reusesNotInformedSentinelsForAllProfileFieldsWithoutLeavingIcpMissing() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        transcript.messages.put("message-pronoun-unknown", message("message-pronoun-unknown",
                "Prefiro não responder."));
        transcript.messages.put("message-first-unknown", message("message-first-unknown",
                "Prefiro não informar."));
        transcript.messages.put("message-occupation-unknown", message("message-occupation-unknown",
                "Não quero informar."));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "PRONOUN_PREFERENCE", "value", "PREFER_NOT_TO_ANSWER",
                        "confidence", "TENTATIVE"), context("message-pronoun-unknown"));
        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "FIRST_TIME_HIRING", "value", "NÃO INFORMADO",
                        "confidence", "CONFIRMED"), context("message-first-unknown"));
        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "OCCUPATION", "value", "prefiro não responder",
                        "confidence", "CONFIRMED"), context("message-occupation-unknown"));

        assertThat(facts.values).extracting(CustomerFact::value)
                .containsExactly("NÃO INFORMADO", "NÃO INFORMADO", "NÃO INFORMADO");
        assertThat(facts.values).extracting(CustomerFact::confidence)
                .containsOnly(FactConfidence.CONFIRMED);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) tools.execute(DomainToolName.GET_CUSTOMER_PROFILE,
                "contact-1", Map.of(), context("message-occupation-unknown")).get("missingIcpFields");
        assertThat(missing).isEmpty();
    }

    @Test
    void confirmsTheCorrectedServiceWhenTheSameMessageNegatesThePreviousNeed() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        transcript.messages.put("message-correction", message("message-correction",
                "Corrigindo: não quero só decoração; quero Decor Pintura para a sala."));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        Map<String, Object> result = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "SELECTED_SERVICE", "value", "Decor Pintura", "confidence", "CONFIRMED"),
                context("message-correction"));

        assertThat(result).containsEntry("confidence", "CONFIRMED");
        assertThat(facts.values).singleElement().extracting(CustomerFact::value).isEqualTo("DECOR_PINTURA");
    }

    @Test
    void listsOnlyCanonicalServicesWithTheSafeRichCatalogFields() {
        MemoryConversation conversations = new MemoryConversation();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, new MemoryFacts(), new MemoryTranscript());

        Map<String, Object> result = tools.execute(DomainToolName.LIST_AVAILABLE_SERVICES, "contact-1",
                Map.of(), context("message-services"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) result.get("services");
        assertThat(services).hasSize(4).allSatisfy(service -> {
            assertThat(service).containsKeys("scope", "areaRule", "deliverables", "process",
                    "responsibilities", "exclusions", "support", "resources");
            assertThat(service.get("serviceType")).isNotEqualTo("DECOR");
        });
    }

    @Test
    void requiresDurableAcceptanceBeforePreparingPayment() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);
        List<CustomerFact> icp = List.of(
                CustomerFact.confirmed("contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "m0", NOW),
                CustomerFact.confirmed("contact-1", "FIRST_TIME_HIRING", "YES", "m0", NOW),
                CustomerFact.confirmed("contact-1", "OCCUPATION", "DESIGNER", "m0", NOW));
        facts.values.addAll(icp);
        conversation = policy.presentTerms(policy.selectService(conversation, "DECOR", NOW), icp, NOW);
        conversations.value = conversation;
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);

        assertThatThrownBy(() -> tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR", "method", "PIX"), context("message-terms")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> assertThat(((DomainToolInvocationUseCase.DomainRejectionException) error).code())
                        .isEqualTo("TERMS_NOT_ACCEPTED"));
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
        assertThat(conversations.value.paymentStatus())
                .isEqualTo(PaymentStatus.NOT_STARTED);
    }

    @Test
    void rejectsAnUnsupportedPaymentMethodWithACommercialCorrectionWithoutReaskingTerms() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        AuditedAcceptance audited = auditedAccepted(policy, conversations, "contact-1");
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);
        tools.setTermsAcceptanceUseCase(audited.useCase());

        assertThatThrownBy(() -> tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR", "method", "link"), context("message-method")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> {
                    DomainToolInvocationUseCase.DomainRejectionException rejection =
                            (DomainToolInvocationUseCase.DomainRejectionException) error;
                    assertThat(rejection.code()).isEqualTo("PAYMENT_METHOD_INVALID");
                    assertThat(rejection.nextAction()).isEqualTo("ASK_FOR_PAYMENT_METHOD");
                    assertThat(rejection.customerMessage()).contains("PIX", "cartão de crédito")
                            .doesNotContainIgnoringCase("termos", "aceite", "sistema", "ferramenta");
                });
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.ACCEPTED);
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
    }

    @Test
    void reportsMissingPaymentMethodWithoutClassifyingItAsMissingTerms() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        ReceptionConversation accepted = policy.acceptTerms(policy.presentTerms(
                policy.selectService(ReceptionConversation.start("contact-1", NOW), "DECOR", NOW),
                List.of(), NOW), NOW);
        conversations.value = accepted;
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations,
                new MemoryFacts(), new MemoryTranscript());

        assertThatThrownBy(() -> tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR"), context("message-method-missing")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> {
                    DomainToolInvocationUseCase.DomainRejectionException rejection =
                            (DomainToolInvocationUseCase.DomainRejectionException) error;
                    assertThat(rejection.code()).isEqualTo("PAYMENT_METHOD_INVALID");
                    assertThat(rejection.missingFields()).containsExactly("method");
                    assertThat(rejection.customerMessage()).contains("PIX", "cartão de crédito");
                });
    }

    @Test
    void reportsServiceMismatchWithoutClassifyingItAsMissingTerms() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        ReceptionConversation accepted = policy.acceptTerms(policy.presentTerms(
                policy.selectService(ReceptionConversation.start("contact-1", NOW), "DECOR_PINTURA", NOW),
                List.of(), NOW), NOW);
        conversations.value = accepted;
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations,
                new MemoryFacts(), new MemoryTranscript());

        assertThatThrownBy(() -> tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR_INTERIORES", "method", "PIX"), context("message-mismatch")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> {
                    DomainToolInvocationUseCase.DomainRejectionException rejection =
                            (DomainToolInvocationUseCase.DomainRejectionException) error;
                    assertThat(rejection.code()).isEqualTo("SERVICE_NOT_CONFIRMED");
                    assertThat(rejection.nextAction()).isEqualTo("CONFIRM_SERVICE");
                    assertThat(rejection.missingFields()).containsExactly("serviceType");
                    assertThat(rejection.customerMessage()).doesNotContainIgnoringCase("termos", "aceite");
                });
        assertThat(conversations.value.selectedService()).isEqualTo("DECOR_PINTURA");
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
    }

    @Test
    void acceptsTermsWhenAcceptanceIsEmbeddedInTheInboundPaymentPreference() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        facts.values.addAll(List.of(
                CustomerFact.confirmed("contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "m0", NOW),
                CustomerFact.confirmed("contact-1", "FIRST_TIME_HIRING", "YES", "m0", NOW),
                CustomerFact.confirmed("contact-1", "OCCUPATION", "DESIGNER", "m0", NOW)));
        AuditedAcceptance audited = auditedAccepted(policy, conversations, "contact-1");
        transcript.messages.put("message-terms-and-method", message("message-terms-and-method",
                "Aceito os termos e prefiro PIX."));
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);
        tools.setTermsAcceptanceUseCase(audited.useCase());

        Map<String, Object> result = tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR", "method", "PIX"), context("message-terms-and-method"));

        assertThat(result).containsEntry("status", "PREPARED");
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.ACCEPTED);
    }

    @Test
    void doesNotReissuePaymentInstructionAfterPaymentWasConfirmed() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        AuditedAcceptance audited = auditedAccepted(policy, conversations, "contact-1");
        ReceptionConversation confirmed = policy.approvePaymentProof(
                policy.receivePaymentProof(
                        policy.preparePayment(audited.conversation(), List.of(), "PIX", NOW), NOW), NOW);
        conversations.value = confirmed;
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations,
                new MemoryFacts(), new MemoryTranscript());
        tools.setTermsAcceptanceUseCase(audited.useCase());

        Map<String, Object> result = tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR", "method", "PIX"), context("message-payment-replay"));

        assertThat(result).containsEntry("status", "CONFIRMED")
                .containsEntry("serviceType", "DECOR_INTERIORES")
                .containsEntry("nextAction", "NONE")
                .containsEntry("customerMessage", "O pagamento já foi confirmado pela arquiteta.")
                .doesNotContainKey("instruction");
        assertThat(conversations.value.paymentStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void rejectsOkAsInsufficientAcceptanceAndUsesTheStructuredSafeBusinessRejection() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = policy.presentTerms(
                policy.selectService(ReceptionConversation.start("contact-1", NOW), "DECOR", NOW),
                List.of(), NOW);
        transcript.messages.put("message-ok", message("message-ok", "ok"));
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);

        assertThatThrownBy(() -> tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR", "method", "PIX"), context("message-ok")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> {
                    DomainToolInvocationUseCase.DomainRejectionException rejection =
                            (DomainToolInvocationUseCase.DomainRejectionException) error;
                    assertThat(rejection.code()).isEqualTo("TERMS_NOT_ACCEPTED");
                    assertThat(rejection.nextAction()).isEqualTo("ASK_FOR_CLEAR_ACCEPTANCE");
                    assertThat(rejection.customerMessage()).contains("aceite claro");
                    assertThat(rejection.customerMessage()).doesNotContainIgnoringCase("system", "api", "exception");
                });
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
    }

    @Test
    void acceptsTheTwoApprovedExplicitTermsPhrasesWithTheDecorAlias() {
        for (String acceptance : List.of("aceito os termos", "concordo com os termos")) {
            CommercialPolicyService policy = new CommercialPolicyService();
            MemoryConversation conversations = new MemoryConversation();
            MemoryFacts facts = new MemoryFacts();
            MemoryTranscript transcript = new MemoryTranscript();
            AuditedAcceptance audited = auditedAccepted(policy, conversations, "contact-1");
            transcript.messages.put("message-accepted", message("message-accepted", acceptance));
            StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);
            tools.setTermsAcceptanceUseCase(audited.useCase());

            Map<String, Object> result = tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                    Map.of("serviceType", "DECOR", "method", "PIX"), context("message-accepted"));

            assertThat(result).containsEntry("status", "PREPARED")
                    .containsEntry("serviceType", "DECOR_INTERIORES");
            assertThat(conversations.value.selectedService()).isEqualTo("DECOR_INTERIORES");
            assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.ACCEPTED);
        }
    }

    @Test
    void rejectsAnExplicitTermsRefusalEvenWhenItContainsTheWordConcordo() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        List<CustomerFact> icp = List.of(
                CustomerFact.confirmed("contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "m0", NOW),
                CustomerFact.confirmed("contact-1", "FIRST_TIME_HIRING", "YES", "m0", NOW),
                CustomerFact.confirmed("contact-1", "OCCUPATION", "DESIGNER", "m0", NOW));
        facts.values.addAll(icp);
        conversations.value = policy.presentTerms(
                policy.selectService(ReceptionConversation.start("contact-1", NOW), "DECOR", NOW), icp, NOW);
        transcript.messages.put("message-refusal", message("message-refusal", "Não concordo"));
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);

        assertThatThrownBy(() -> tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR", "method", "PIX"), context("message-refusal")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> assertThat(((DomainToolInvocationUseCase.DomainRejectionException) error).code())
                        .isEqualTo("TERMS_NOT_ACCEPTED"));

        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
        assertThat(conversations.value.paymentStatus())
                .isEqualTo(br.com.urbana.connect.domain.reception.model.PaymentStatus.NOT_STARTED);
    }

    @Test
    void persistsServiceSelectionBeforeTermsSoVersionedTransitionsDoNotSkipARevision() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        facts.values.addAll(List.of(
                CustomerFact.confirmed("contact-1", "PRONOUN_PREFERENCE", "ELA_DELA", "m0", NOW),
                CustomerFact.confirmed("contact-1", "FIRST_TIME_HIRING", "YES", "m0", NOW),
                CustomerFact.confirmed("contact-1", "OCCUPATION", "DESIGNER", "m0", NOW)));
        conversations.value = ReceptionConversation.start("contact-1", NOW)
                .bindContractingUnit("unit-1", "sala", "message-environment", NOW);
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);

        tools.execute(DomainToolName.PREPARE_TERMS, "contact-1", Map.of("serviceType", "DECOR"), context("message-terms"));

        assertThat(conversations.savedVersions).containsExactly(2L, 3L);
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
    }

    @Test
    void rejectsLateFactMutationAfterHumanHandoffEvenWhenTheLeaseIsStillRunning() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW)
                .requestHumanHandoff("cliente pediu uma pessoa", NOW);
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        assertThatThrownBy(() -> tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "OCCUPATION", "value", "DESIGNER", "confidence", "CONFIRMED"),
                context("message-late")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> {
                    DomainToolInvocationUseCase.DomainRejectionException rejection =
                            (DomainToolInvocationUseCase.DomainRejectionException) error;
                    assertThat(rejection.code()).isEqualTo("HUMAN_OWNS_CONVERSATION");
                    assertThat(rejection.customerMessage()).doesNotContainIgnoringCase(
                            "system", "tool", "api", "database", "exception", "retry");
                });
        assertThat(facts.values).isEmpty();
        assertThat(conversations.savedVersions).isEmpty();
    }

    @Test
    void handoffPersistsOneCanonicalAckAndReplaysTheSameTransitionWithoutDuplicates() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("conversation-1", "contact-1", NOW)
                .bindContractingUnit("unit-1", "sala", "message-environment", NOW);
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        Map<String, Object> first = tools.execute(DomainToolName.REQUEST_HUMAN_HANDOFF, "contact-1",
                Map.of("reason", "cliente pediu uma pessoa"), context("message-1"));
        Map<String, Object> replay = tools.execute(DomainToolName.REQUEST_HUMAN_HANDOFF, "contact-1",
                Map.of("reason", "cliente pediu uma pessoa"), context("message-1"));

        assertThat(first).containsEntry("status", "TRANSFERRED")
                .containsEntry("ownership", "HUMAN")
                .containsEntry("ackMessage", "Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.");
        assertThat(replay).isEqualTo(first);
        assertThat(conversations.value.mode())
                .isEqualTo(br.com.urbana.connect.domain.reception.model.ReceptionMode.HUMAN);
        assertThat(transcript.messages.values())
                .filteredOn(message -> message.senderType() == ReceptionMessageSender.URBA)
                .singleElement()
                .extracting(ReceptionMessage::text)
                .isEqualTo("Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.");
    }

    @Test
    void prepareTermsKeepsTheCommercialResultAndRecordsOneOpaqueIcpSkipObservation() {
        CommercialPolicyService policy = spy(new CommercialPolicyService());
        doReturn(List.of("PRONOUN_PREFERENCE", "FIRST_TIME_HIRING", "OCCUPATION"))
                .when(policy).missingIcpFields(anyCollection(), eq(NOW));
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryIcpObservations observations = new MemoryIcpObservations();
        conversations.value = ReceptionConversation.start("conversation-1", "contact-1", NOW)
                .bindContractingUnit("unit-1", "sala", "message-environment", NOW);
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts,
                transcript, observations);

        Map<String, Object> first = tools.execute(DomainToolName.PREPARE_TERMS, "contact-1",
                Map.of("serviceType", "DECOR_PINTURA"), context("message-terms"));
        Map<String, Object> replay = tools.execute(DomainToolName.PREPARE_TERMS, "contact-1",
                Map.of("serviceType", "DECOR_PINTURA"), context("message-terms"));

        assertThat(replay).isEqualTo(first)
                .containsEntry("status", "PRESENTED")
                .containsEntry("serviceType", "DECOR_PINTURA");
        assertThat(first).doesNotContainKeys("event", "eventType", "missingIcpFields", "turnId",
                "conversationId", "idempotencyKey");
        assertThat(observations.events).hasSize(1);
        IcpObservationEvent event = observations.events.getFirst();
        assertThat(event.eventType()).isEqualTo(IcpObservationEvent.TYPE);
        assertThat(event.serviceType()).isEqualTo("DECOR_PINTURA");
        assertThat(event.missingFields()).containsExactly("PRONOUN_PREFERENCE", "FIRST_TIME_HIRING",
                "OCCUPATION");
        assertThat(event.detectionPoint()).isEqualTo("PREPARE_TERMS");
        assertThat(event.toString()).doesNotContain("message-terms", "contact-1", "ela", "designer");
    }

    @Test
    void handoffEmitsOneInternalNotificationBeforeBlockingAndReplaysAsANoop() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        MemoryIcpObservations observations = new MemoryIcpObservations();
        MemoryHandoffNotifications notifications = new MemoryHandoffNotifications();
        conversations.value = ReceptionConversation.start("conversation-1", "contact-1", NOW);
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript, observations, notifications);

        Map<String, Object> first = tools.execute(DomainToolName.REQUEST_HUMAN_HANDOFF, "contact-1",
                Map.of("reason", "cliente pediu uma pessoa"), context("message-handoff"));
        Map<String, Object> replay = tools.execute(DomainToolName.REQUEST_HUMAN_HANDOFF, "contact-1",
                Map.of("reason", "cliente pediu uma pessoa"), context("message-handoff"));

        assertThat(replay).isEqualTo(first);
        assertThat(notifications.events).hasSize(1);
        HumanHandoffNotification notification = notifications.events.getFirst();
        assertThat(notification.conversationId()).isEqualTo("conversation-1");
        assertThat(notification.turnId()).isEqualTo("turn-1");
        assertThat(notification.reason()).isEqualTo("cliente pediu uma pessoa");
        assertThat(notification.idempotencyKey()).isEqualTo(first.get("handoffId"));
        assertThat(notification.toString()).doesNotContain("contact-1", "message-handoff");
    }

    @Test
    void tentativeEnvironmentEvidenceDoesNotBindAContractingUnitOrPermitTerms() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("conversation-1", "contact-1", NOW);
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        Map<String, Object> recorded = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "ENVIRONMENT", "value", "sala", "confidence", "CONFIRMED"),
                context("message-without-environment"));

        assertThat(recorded).containsEntry("confidence", "TENTATIVE");
        assertThat(conversations.value.contractingUnitId()).isNull();
        ToolExecutionContext termsContext = context("message-without-environment");
        Map<String, Object> decorArguments = Map.of("serviceType", "DECOR");
        assertThatThrownBy(() -> tools.execute(DomainToolName.PREPARE_TERMS, "contact-1",
                decorArguments, termsContext))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> assertThat(((DomainToolInvocationUseCase.DomainRejectionException) error).code())
                        .isEqualTo("ENVIRONMENT_NOT_CONFIRMED"));
    }

    @Test
    void requiresTheBackendExecutionContextAndValidContactForStatefulTools() {
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                new MemoryConversation(), new MemoryFacts());
        ToolExecutionContext context = context("message");
        Map<String, Object> noArguments = Map.of();

        assertThatThrownBy(() -> tools.execute(DomainToolName.GET_CUSTOMER_PROFILE, "contact-1", noArguments))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("execution context");
        assertThatThrownBy(() -> tools.execute(null, "contact-1", noArguments, context))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> tools.execute(DomainToolName.GET_CUSTOMER_PROFILE, " ", noArguments, context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contactId");
        assertThatThrownBy(() -> tools.execute(DomainToolName.GET_CUSTOMER_PROFILE, "contact-1", noArguments, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordsProfilePreviousServicesAndUsesSafeRejectionsForInvalidFactsAndHandoffs() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("conversation-1", "contact-1", NOW);
        transcript.messages.put("message-service", message("message-service", "Quero Decor."));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "SERVICE", "value", "Decor", "confidence", "CONFIRMED"),
                context("message-service"));
        Map<String, Object> profile = tools.execute(DomainToolName.GET_CUSTOMER_PROFILE, "contact-1",
                Map.of(), context("message-service"));
        @SuppressWarnings("unchecked")
        List<String> previousServices = (List<String>) profile.get("previousServices");
        assertThat(previousServices).containsExactly("DECOR_INTERIORES");

        assertThatThrownBy(() -> tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "UNKNOWN", "value", "x"), context("message-service")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> assertThat(((DomainToolInvocationUseCase.DomainRejectionException) error).code())
                        .isEqualTo("CUSTOMER_INFORMATION_INVALID"));
        assertThatThrownBy(() -> tools.execute(DomainToolName.REQUEST_HUMAN_HANDOFF, "contact-1",
                Map.of(), context("message-service")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> assertThat(((DomainToolInvocationUseCase.DomainRejectionException) error).code())
                        .isEqualTo("HANDOFF_REASON_REQUIRED"));
    }

    @Test
    void handlesMissingTranscriptAndEvidenceAliasesWithoutForgingConfirmedFacts() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        StatefulDomainToolService noTranscript = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts);

        Map<String, Object> unsupported = noTranscript.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "OCCUPATION", "value", "DESIGNER", "confidence", "CONFIRMED"),
                context("message-missing"));
        assertThat(unsupported).containsEntry("confidence", "TENTATIVE")
                .containsEntry("value", "DESIGNER");
        assertThat(facts.values).singleElement().extracting(CustomerFact::sourceMessageId)
                .isEqualTo("message-missing");

        MemoryTranscript transcript = new MemoryTranscript();
        transcript.messages.put("message-service-alias", message("message-service-alias", "Quero contratar Decor."));
        StatefulDomainToolService withTranscript = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);
        Map<String, Object> service = withTranscript.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "SELECTED_SERVICE", "value", "Decor", "confidence", "CONFIRMED"),
                context("message-service-alias"));
        assertThat(service).containsEntry("value", "DECOR_INTERIORES")
                .containsEntry("confidence", "CONFIRMED");
    }

    @Test
    void preservesWordBoundariesWhenConfirmingEnvironmentEvidence() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        transcript.messages.put("message-prefix", message("message-prefix", "Quero contratar para o salao."));
        transcript.messages.put("message-exact", message("message-exact", "Quero contratar para a sala."));
        StatefulDomainToolService tools = new StatefulDomainToolService(new CommercialPolicyService(),
                conversations, facts, transcript);

        Map<String, Object> prefix = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "ENVIRONMENT", "value", "sala", "confidence", "CONFIRMED"),
                context("message-prefix"));
        Map<String, Object> exact = tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "ENVIRONMENT", "value", "sala", "confidence", "CONFIRMED"),
                context("message-exact"));

        assertThat(prefix).containsEntry("confidence", "TENTATIVE");
        assertThat(exact).containsEntry("confidence", "CONFIRMED");
        assertThat(conversations.value.environmentSourceMessageId()).isEqualTo("message-exact");
    }

    @Test
    void returnsStablePaymentResultsForPreparedProofAndRejectedStates() {
        CommercialPolicyService policy = new CommercialPolicyService();
        for (PaymentStatus status : List.of(PaymentStatus.PREPARED, PaymentStatus.PROOF_RECEIVED,
                PaymentStatus.REJECTED, PaymentStatus.CONFIRMED)) {
            MemoryConversation conversations = new MemoryConversation();
            MemoryFacts facts = new MemoryFacts();
            MemoryTranscript transcript = new MemoryTranscript();
            AuditedAcceptance audited = auditedAccepted(policy, conversations, "contact-1");
            ReceptionConversation current = audited.conversation();
            if (status == PaymentStatus.PREPARED) {
                current = policy.preparePayment(current, List.of(), "PIX", NOW.plusSeconds(3));
            } else if (status == PaymentStatus.PROOF_RECEIVED) {
                current = policy.receivePaymentProof(
                        policy.preparePayment(current, List.of(), "PIX", NOW.plusSeconds(3)), NOW.plusSeconds(4));
            } else if (status == PaymentStatus.REJECTED) {
                current = policy.receivePaymentProof(
                                policy.preparePayment(current, List.of(), "PIX", NOW.plusSeconds(3)), NOW.plusSeconds(4))
                        .rejectPayment(NOW.plusSeconds(5));
            } else {
                current = policy.approvePaymentProof(
                        policy.receivePaymentProof(
                                policy.preparePayment(current, List.of(), "PIX", NOW.plusSeconds(3)), NOW.plusSeconds(4)),
                        NOW.plusSeconds(5));
            }
            conversations.value = current;
            StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);
            tools.setTermsAcceptanceUseCase(audited.useCase());

            Map<String, Object> result = tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                    Map.of("serviceType", "DECOR", "method", "PIX"), context("message-payment"));
            if (status == PaymentStatus.PREPARED) {
                assertThat(result).containsEntry("status", "ALREADY_PREPARED");
            } else if (status == PaymentStatus.PROOF_RECEIVED) {
                assertThat(result).containsEntry("status", "PROOF_RECEIVED");
            } else if (status == PaymentStatus.CONFIRMED) {
                assertThat(result).containsEntry("status", "CONFIRMED");
            } else {
                assertThat(result).containsEntry("status", "PREPARED");
            }
        }
    }

    @Test
    void keepsTermsTransitionSafeWhenDeclinedOrOptionalObservabilityFails() {
        CommercialPolicyService policy = new CommercialPolicyService();
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW)
                .bindContractingUnit("unit-1", "sala", "message-environment", NOW)
                .selectService("DECOR_INTERIORES", NOW)
                .declineTerms(NOW.plusSeconds(1));
        IcpObservationEventGateway throwingObservations = event -> {
            throw new IllegalStateException("optional sink unavailable");
        };
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript,
                throwingObservations);

        Map<String, Object> result = tools.execute(DomainToolName.PREPARE_TERMS, "contact-1",
                Map.of("serviceType", "DECOR"), context("message-terms"));

        assertThat(result).containsEntry("status", "PRESENTED");
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.PRESENTED);
    }

    private static ToolExecutionContext context(String sourceMessageId) {
        return context(sourceMessageId, List.of(sourceMessageId));
    }

    private static ToolExecutionContext context(String sourceMessageId, List<String> sourceMessageIds) {
        return new ToolExecutionContext(new ActiveTurnLease("session-1", "turn-1", "contact-1",
                sourceMessageId, ActiveTurnLeaseStatus.RUNNING, NOW, NOW.plusSeconds(30), null, 0,
                "claim-1", sourceMessageIds), NOW);
    }

    private static ReceptionMessage message(String id, String text) {
        return new ReceptionMessage(id, id, "corr-1", "conversation-1", "contact-1",
                ReceptionMessageDirection.INBOUND, ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT,
                text, null, id, NOW);
    }

    private static AuditedAcceptance auditedAccepted(CommercialPolicyService policy,
                                                      MemoryConversation conversations,
                                                      String contactId) {
        MemoryAudits audits = new MemoryAudits();
        ReceptionConversation presented = policy.presentTerms(
                policy.selectService(ReceptionConversation.start("conversation-1", contactId, NOW), "DECOR", NOW),
                List.of(), NOW);
        presented = presented.bindContractingUnit("unit-1", "sala", "message-environment", NOW.minusSeconds(1));
        // Binding the unit invalidates the prior selection, so select and
        // present again against the canonical contracting unit.
        presented = policy.presentTerms(
                policy.selectService(presented, "DECOR", NOW), List.of(), NOW);
        TermsConsentAudit audit = new TermsConsentAudit("presentation-1", presented.id(), contactId, "turn-terms",
                "unit-1", "sala", "message-environment", presented.selectedService(),
                policy.termsUrl(presented.selectedService()), "v1", "invoke-terms", "outbound-terms", NOW,
                null, null, null, null, NOW, TermsConsentStatus.PRESENTED, presented.version(), null);
        audits.savePresentationIfAbsent(audit);
        ReceptionConversation withConsent = presented.activateTermsConsent("presentation-1", NOW.plusSeconds(1));
        ReceptionConversation accepted = policy.acceptTerms(withConsent, "Aceito", NOW.plusSeconds(2));
        audits.acceptIfPresented("presentation-1", "event-accept", "message-accept", "Aceito",
                accepted.version(), NOW.plusSeconds(2));
        conversations.value = accepted;
        return new AuditedAcceptance(accepted, new br.com.urbana.connect.application.reception.TermsAcceptanceUseCase(audits));
    }

    private record AuditedAcceptance(ReceptionConversation conversation,
                                     br.com.urbana.connect.application.reception.TermsAcceptanceUseCase useCase) { }

    private static final class MemoryConversation implements ReceptionConversationGateway {
        ReceptionConversation value;
        final List<Long> savedVersions = new ArrayList<>();
        @Override public Optional<ReceptionConversation> findByContactId(String contactId) { return Optional.ofNullable(value); }
        @Override public ReceptionConversation save(ReceptionConversation conversation) {
            savedVersions.add(conversation.version());
            return value = conversation;
        }
    }

    private static final class MemoryAudits implements TermsConsentAuditGateway {
        private final Map<String, TermsConsentAudit> values = new HashMap<>();

        @Override
        public Optional<TermsConsentAudit> findByPresentationId(String id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Optional<TermsConsentAudit> findPresented(String conversationId, String unitId) {
            return values.values().stream().filter(value -> value.conversationId().equals(conversationId)
                    && value.contractingUnitId().equals(unitId)
                    && value.status() == TermsConsentStatus.PRESENTED).findFirst();
        }

        @Override
        public TermsConsentAudit savePresentationIfAbsent(TermsConsentAudit audit) {
            return values.computeIfAbsent(audit.presentationId(), ignored -> audit);
        }

        @Override
        public TermsConsentAudit acceptIfPresented(String id, String eventId, String messageId, String text,
                                                   long version, Instant at) {
            TermsConsentAudit current = findByPresentationId(id).orElseThrow();
            TermsConsentAudit accepted = current.accept(messageId, eventId, text, at, version);
            values.put(id, accepted);
            return accepted;
        }
    }

    private static final class MemoryIcpObservations implements IcpObservationEventGateway {
        final List<IcpObservationEvent> events = new ArrayList<>();

        @Override
        public boolean appendIfAbsent(IcpObservationEvent event) {
            if (events.stream().anyMatch(existing -> existing.idempotencyKey().equals(event.idempotencyKey()))) {
                return false;
            }
            events.add(event);
            return true;
        }
    }

    private static final class MemoryHandoffNotifications implements HumanHandoffNotificationGateway {
        final List<HumanHandoffNotification> events = new ArrayList<>();

        @Override
        public boolean notifyIfAbsent(HumanHandoffNotification notification) {
            if (events.stream().anyMatch(existing -> existing.idempotencyKey().equals(notification.idempotencyKey()))) {
                return false;
            }
            events.add(notification);
            return true;
        }
    }

    private static final class MemoryFacts implements CustomerFactGateway {
        final List<CustomerFact> values = new ArrayList<>();
        @Override public List<CustomerFact> findCurrentByContactId(String contactId, Instant at) {
            return values.stream().filter(f -> f.contactId().equals(contactId) && f.isCurrentAt(at)
                    && f.supersededBy() == null).toList();
        }
        @Override public List<CustomerFact> findByContactId(String contactId) { return values; }
        @Override public CustomerFact save(CustomerFact fact) {
            values.removeIf(existing -> existing.id().equals(fact.id()));
            values.add(fact);
            return fact;
        }
    }

    private static final class MemoryTranscript implements ReceptionTranscriptGateway {
        final Map<String, ReceptionMessage> messages = new HashMap<>();
        @Override public boolean appendIfAbsent(ReceptionMessage message) { return messages.putIfAbsent(message.eventId(), message) == null; }
        @Override public Optional<ReceptionMessage> findByEventId(String eventId) { return Optional.ofNullable(messages.get(eventId)); }
        @Override public List<ReceptionMessage> findByConversationId(String conversationId) { return List.copyOf(messages.values()); }
    }
}
