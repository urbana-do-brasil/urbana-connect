package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NonProspectPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    private final NonProspectPolicy policy = new NonProspectPolicy();

    @Test
    void identifiesUrbaAndUrbanaBrieflyWithoutStartingIcp() {
        NonProspectPolicy.Decision decision = policy.decide("Quem está respondendo por aqui?");

        assertThat(decision.disposition()).isEqualTo(NonProspectPolicy.Disposition.IDENTIFY);
        assertThat(decision.commercialDecision())
                .isEqualTo(NonProspectPolicy.CommercialDecision.DO_NOT_INFER_PURCHASE);
        assertThat(decision.output().message())
                .containsIgnoringCase("Urba")
                .containsIgnoringCase("Urbana do Brasil")
                .doesNotContain("?");
        assertThat(decision.output().nextAction()).isEqualTo(AgentNextAction.AWAIT_CUSTOMER);
        assertThat(decision.shouldCollectIcp()).isFalse();
        assertThat(decision.shouldProgressCommercialFlow()).isFalse();
        assertThat(decision.nextState()).isEqualTo(NonProspectPolicy.State.initial());
    }

    @Test
    void allowsOnlyOneLightProbeAndThenClosesWithoutCommercialProgression() {
        NonProspectPolicy.State initial = NonProspectPolicy.State.initial();

        NonProspectPolicy.Decision first = policy.decide(
                "Oi, queria saber se este é o canal certo para uma dúvida.", initial);

        assertThat(first.disposition()).isEqualTo(NonProspectPolicy.Disposition.LIGHT_PROBE);
        assertThat(first.output().nextAction()).isEqualTo(AgentNextAction.AWAIT_CUSTOMER);
        assertThat(first.output().message()).contains("?");
        assertThat(first.nextState().lightProbesUsed()).isEqualTo(1);
        assertThat(initial.lightProbesUsed()).isZero();

        NonProspectPolicy.Decision second = policy.decide(
                "Ainda é só uma dúvida geral, sem contratação.", first.nextState());

        assertThat(second.disposition()).isEqualTo(NonProspectPolicy.Disposition.CLOSE);
        assertThat(second.output().nextAction()).isEqualTo(AgentNextAction.NONE);
        assertThat(second.output().message()).doesNotContain("?");
        assertThat(second.nextState().lightProbesUsed()).isEqualTo(1);
        assertThat(second.shouldCollectIcp()).isFalse();
        assertThat(second.shouldProgressCommercialFlow()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Desculpa, número errado.",
            "Estou procurando uma farmácia; este assunto não tem relação com a Urbana."
    })
    void closesWrongOrUnrelatedContactsWithoutCollectingIcp(String message) {
        NonProspectPolicy.Decision decision = policy.decide(message);

        assertThat(decision.disposition()).isEqualTo(NonProspectPolicy.Disposition.CLOSE);
        assertThat(decision.output().nextAction()).isEqualTo(AgentNextAction.NONE);
        assertThat(decision.output().message()).isNotBlank();
        assertThat(decision.shouldCollectIcp()).isFalse();
        assertThat(decision.shouldProgressCommercialFlow()).isFalse();
        assertThat(decision.commercialDecision())
                .isEqualTo(NonProspectPolicy.CommercialDecision.DO_NOT_INFER_PURCHASE);
    }

    @Test
    void offersHumanServiceForAnUnconfirmedInstitutionalRequestWithoutCreatingAnOpportunity() {
        NonProspectPolicy.Decision decision = policy.decide(
                "Gostaria de tratar de uma parceria institucional, mas não sei quem cuida disso.");

        assertThat(decision.disposition()).isEqualTo(NonProspectPolicy.Disposition.OFFER_HUMAN);
        assertThat(decision.output().nextAction()).isEqualTo(AgentNextAction.HANDOFF);
        assertThat(decision.output().handoffReason()).containsIgnoringCase("institucional");
        assertThat(decision.output().message()).containsIgnoringCase("atendimento humano");
        assertThat(decision.shouldCollectIcp()).isFalse();
        assertThat(decision.shouldProgressCommercialFlow()).isFalse();
    }

    @Test
    void doesNotMutateAuthoritativeFactsConversationOrPolicyState() {
        CustomerFact occupation = CustomerFact.confirmed(
                "contact-1", "OCCUPATION", "DESIGNER", "message-1", NOW);
        List<CustomerFact> facts = new ArrayList<>(List.of(occupation));
        ReceptionConversation conversation = ReceptionConversation.start("contact-1", NOW);
        NonProspectPolicy.State state = NonProspectPolicy.State.initial();

        NonProspectPolicy.Decision decision = policy.decide(
                "Quem responde neste número?", state, facts, conversation);

        assertThat(facts).containsExactly(occupation);
        assertThat(conversation).isEqualTo(ReceptionConversation.start(
                conversation.id(), "contact-1", NOW));
        assertThat(state).isEqualTo(NonProspectPolicy.State.initial());
        assertThat(decision.shouldCollectIcp()).isFalse();
        assertThat(decision.shouldProgressCommercialFlow()).isFalse();
        assertThat(decision.commercialDecision())
                .isEqualTo(NonProspectPolicy.CommercialDecision.DO_NOT_INFER_PURCHASE);
    }
}
