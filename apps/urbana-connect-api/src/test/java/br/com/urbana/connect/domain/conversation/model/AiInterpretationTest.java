package br.com.urbana.connect.domain.conversation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiInterpretationTest {

    @Test
    void shouldReturnUnknownInterpretationWithNullServiceAndResponse() {
        AiInterpretation interpretation = AiInterpretation.unknown();

        assertThat(interpretation.intent()).isEqualTo(IntentType.UNKNOWN);
        assertThat(interpretation.selectedService()).isNull();
        assertThat(interpretation.suggestedResponse()).isNull();
    }
}
