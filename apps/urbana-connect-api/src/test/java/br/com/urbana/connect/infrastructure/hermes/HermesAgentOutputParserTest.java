package br.com.urbana.connect.infrastructure.hermes;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HermesAgentOutputParserTest {

    private final HermesAgentOutputParser parser = new HermesAgentOutputParser();

    @ParameterizedTest
    @ValueSource(strings = {"", "  \n\t"})
    void rejectsEmptyOrBlankConversationContentBeforeItCanBePublished(String content) {
        assertThatThrownBy(() -> parser.parse(content))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentOutput(content, AgentNextAction.NONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesLegacyOperationalEnvelopeOnlyAtTheExplicitOperationalBoundary() {
        AgentOutput output = parser.parseOperationalEnvelope(
                "{\"message\":\"Olá!\",\"nextAction\":\"AWAIT_CUSTOMER\",\"handoffReason\":null}");

        assertThat(output.message()).isEqualTo("Olá!");
        assertThat(output.nextAction()).isEqualTo(AgentNextAction.AWAIT_CUSTOMER);
        assertThat(output.handoffReason()).isNull();
    }

    @Test
    void preservesNaturalTextAndDoesNotRequireNextActionForConversation() {
        String content = "  texto livre do Hermes — com pontuação  \n";

        AgentOutput output = parser.parse(content);

        assertThat(output.message()).isEqualTo(content);
        assertThat(output.nextAction()).isEqualTo(AgentNextAction.NONE);
    }

    @Test
    void extractsOnlyTheMessageFromACompatibleLegacyEnvelopeWithoutRewritingIt() {
        String message = "  resposta legada  ";

        AgentOutput output = parser.parse("{\"message\":\"  resposta legada  \"}");

        assertThat(output.message()).isEqualTo(message);
        assertThat(output.nextAction()).isEqualTo(AgentNextAction.NONE);
    }

    @Test
    void keepsStrictValidationAvailableForOperationalEnvelopeConsumers() {
        assertThatThrownBy(() -> parser.parseOperationalEnvelope(
                "{\"message\":\"Oi\",\"nextAction\":\"NONE\",\"tool\":\"bad\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseOperationalEnvelope("texto livre"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseOperationalEnvelope(
                "{\"message\":\"Oi\",\"nextAction\":\"HANDOFF\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsStrictEnvelopeSizeValidationSeparateFromLiteralConversationText() {
        String oversized = "a".repeat(4097);
        assertThat(parser.parse(oversized).message()).isEqualTo(oversized);
        assertThatThrownBy(() -> parser.parseOperationalEnvelope("[]"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseOperationalEnvelope(
                "{\"message\":\"" + oversized + "\",\"nextAction\":\"NONE\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
