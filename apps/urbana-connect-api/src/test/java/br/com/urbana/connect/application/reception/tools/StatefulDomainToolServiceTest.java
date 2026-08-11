package br.com.urbana.connect.application.reception.tools;

import br.com.urbana.connect.application.reception.CommercialPolicyService;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.model.FactConfidence;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageDirection;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageSender;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.domain.reception.model.ReceptionTurnStatus;
import br.com.urbana.connect.domain.reception.model.TermsStatus;
import br.com.urbana.connect.domain.reception.port.out.CustomerFactGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatefulDomainToolServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void neverConfirmsAClaimThatIsNotSupportedByTheLeaseBoundInboundMessage() {
        MemoryConversation conversations = new MemoryConversation();
        MemoryFacts facts = new MemoryFacts();
        MemoryTranscript transcript = new MemoryTranscript();
        conversations.value = ReceptionConversation.start("contact-1", NOW);
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
                Map.of("factType", "PRONOMES", "value", "ELA_DELA", "confidence", "CONFIRMED"),
                context("message-pronoun"));
        tools.execute(DomainToolName.UPDATE_CUSTOMER_FACT, "contact-1",
                Map.of("factType", "PROFISSÃO", "value", "DESIGNER", "confidence", "CONFIRMED"),
                context("message-occupation"));

        assertThat(facts.values).extracting(CustomerFact::type)
                .containsExactly("PRONOUN_PREFERENCE", "OCCUPATION");
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
                .containsExactly("ELA_DELA", "YES", "DESIGNER", "DECOR");
        assertThat(facts.values).extracting(CustomerFact::confidence)
                .containsOnly(FactConfidence.CONFIRMED);
        assertThat(conversations.value.selectedService()).isEqualTo("DECOR");
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
    void requiresExplicitAcceptanceFromTheSameInboundMessageBeforePreparingPayment() {
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
        transcript.messages.put("message-terms", message("message-terms", "Aceito"));
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);

        Map<String, Object> result = tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR", "method", "PIX"), context("message-terms"));

        assertThat(result).containsEntry("status", "PREPARED");
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.ACCEPTED);
        assertThat(conversations.value.paymentStatus())
                .isEqualTo(br.com.urbana.connect.domain.reception.model.PaymentStatus.PREPARED);
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
        conversations.value = policy.presentTerms(
                policy.selectService(ReceptionConversation.start("contact-1", NOW), "DECOR", NOW), facts.values, NOW);
        transcript.messages.put("message-terms-and-method", message("message-terms-and-method",
                "Aceito os termos e prefiro PIX."));
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);

        Map<String, Object> result = tools.execute(DomainToolName.PREPARE_PAYMENT, "contact-1",
                Map.of("serviceType", "DECOR", "method", "PIX"), context("message-terms-and-method"));

        assertThat(result).containsEntry("status", "PREPARED");
        assertThat(conversations.value.termsStatus()).isEqualTo(TermsStatus.ACCEPTED);
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
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit terms acceptance");

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
        conversations.value = ReceptionConversation.start("contact-1", NOW);
        StatefulDomainToolService tools = new StatefulDomainToolService(policy, conversations, facts, transcript);

        tools.execute(DomainToolName.PREPARE_TERMS, "contact-1", Map.of("serviceType", "DECOR"), context("message-terms"));

        assertThat(conversations.savedVersions).containsExactly(1L, 2L);
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
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HUMAN");
        assertThat(facts.values).isEmpty();
        assertThat(conversations.savedVersions).isEmpty();
    }

    private static ToolExecutionContext context(String sourceMessageId) {
        return new ToolExecutionContext(new ActiveTurnLease("session-1", "turn-1", "contact-1",
                sourceMessageId, ActiveTurnLeaseStatus.RUNNING, NOW, NOW.plusSeconds(30), null, 0), NOW);
    }

    private static ReceptionMessage message(String id, String text) {
        return new ReceptionMessage(id, id, "corr-1", "conversation-1", "contact-1",
                ReceptionMessageDirection.INBOUND, ReceptionMessageSender.CONTACT, ReceptionMessageType.TEXT,
                text, null, id, NOW);
    }

    private static final class MemoryConversation implements ReceptionConversationGateway {
        ReceptionConversation value;
        final List<Long> savedVersions = new ArrayList<>();
        @Override public Optional<ReceptionConversation> findByContactId(String contactId) { return Optional.ofNullable(value); }
        @Override public ReceptionConversation save(ReceptionConversation conversation) {
            savedVersions.add(conversation.version());
            return value = conversation;
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
