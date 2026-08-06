package br.com.urbana.connect.infrastructure.hermes;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HermesAgentOutputParserTest {

    private final HermesAgentOutputParser parser = new HermesAgentOutputParser();

    @Test
    void parsesOnlyTheMinimalOutputContract() {
        AgentOutput output = parser.parse("{\"message\":\"Olá!\",\"nextAction\":\"AWAIT_CUSTOMER\",\"handoffReason\":null}");

        assertThat(output.message()).isEqualTo("Olá!");
        assertThat(output.nextAction()).isEqualTo(AgentNextAction.AWAIT_CUSTOMER);
        assertThat(output.handoffReason()).isNull();
    }

    @Test
    void rejectsUnknownFieldsMalformedJsonAndInvalidHandoff() {
        assertThatThrownBy(() -> parser.parse("{\"message\":\"Oi\",\"nextAction\":\"NONE\",\"tool\":\"bad\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("texto livre"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("{\"message\":\"Oi\",\"nextAction\":\"HANDOFF\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutputThatIsNotAnObjectOrUsesOversizedText() {
        assertThatThrownBy(() -> parser.parse("[]")).isInstanceOf(IllegalArgumentException.class);
        String oversized = "a".repeat(4097);
        assertThatThrownBy(() -> parser.parse("{\"message\":\"" + oversized + "\",\"nextAction\":\"NONE\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
