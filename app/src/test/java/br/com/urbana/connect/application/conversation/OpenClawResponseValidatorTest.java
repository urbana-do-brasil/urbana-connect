package br.com.urbana.connect.application.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawResponseValidatorTest {

    private final OpenClawResponseValidator validator = new OpenClawResponseValidator();

    @Test
    void shouldAcceptValidReply() {
        OpenClawResponseValidationResult result = validator.validate("  Oi! Posso te ajudar com isso 😊  ", 100);

        assertThat(result.valid()).isTrue();
        assertThat(result.sanitizedText()).isEqualTo("Oi! Posso te ajudar com isso 😊");
    }

    @Test
    void shouldRejectBlankReply() {
        OpenClawResponseValidationResult result = validator.validate("   ", 100);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("empty_reply");
    }

    @Test
    void shouldRejectReplyLongerThanConfiguredLimit() {
        OpenClawResponseValidationResult result = validator.validate("x".repeat(101), 100);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("reply_too_long");
    }

    @Test
    void shouldRejectToolCodeReply() {
        OpenClawResponseValidationResult result = validator.validate("""
            <tool_code>
            print(default_api.read(path='/home/node/.openclaw/workspace/AGENTS.md'))
            </tool_code>
            """, 500);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("tool_output");
    }

    @Test
    void shouldRejectInternalPromptLeakReply() {
        OpenClawResponseValidationResult result = validator.validate(
            "Based on the AGENTS.md file, the persona should answer in Portuguese.",
            500
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("tool_output");
    }
}
