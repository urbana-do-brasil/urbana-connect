package br.com.urbana.connect.infrastructure.whatsapp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppPayloadConstraintsTest {

    @Test
    void shouldTruncateReplyButtonTitleToMetaLimit() {
        String value = WhatsAppPayloadConstraints.replyButtonTitle("✅ Sim, acertou em cheio");

        assertThat(value).isEqualTo("✅ Sim, acertou em...");
        assertThat(value).hasSize(WhatsAppPayloadConstraints.REPLY_BUTTON_TITLE_LIMIT);
    }

    @Test
    void shouldTruncateListRowDescriptionToMetaLimit() {
        String value = WhatsAppPayloadConstraints.listRowDescription(
            "Quero renovar meu espaço interno sem gastar muito, nada de quebra-quebra."
        );

        assertThat(value).isEqualTo("Quero renovar meu espaço interno sem gastar muito, nada de quebra-que...");
        assertThat(value).hasSize(WhatsAppPayloadConstraints.LIST_ROW_DESCRIPTION_LIMIT);
    }

    @Test
    void shouldTruncateListRowTitleToMetaLimit() {
        String value = WhatsAppPayloadConstraints.listRowTitle("🛋️ Decor com um nome muito grande");

        assertThat(value.codePointCount(0, value.length())).isEqualTo(WhatsAppPayloadConstraints.LIST_ROW_TITLE_LIMIT);
        assertThat(value).endsWith("...");
    }

    @Test
    void shouldTruncateInteractiveBodyTextToMetaLimit() {
        String value = WhatsAppPayloadConstraints.interactiveBodyText("x".repeat(1100));

        assertThat(value).hasSize(WhatsAppPayloadConstraints.INTERACTIVE_BODY_TEXT_LIMIT);
        assertThat(value).endsWith("...");
    }

    @Test
    void shouldKeepTextBodyWhenWithinLimit() {
        String value = WhatsAppPayloadConstraints.textBody("Mensagem curta");

        assertThat(value).isEqualTo("Mensagem curta");
    }

    @Test
    void shouldTruncateUsingCodePointsForEmojiStrings() {
        String value = WhatsAppPayloadConstraints.listRowTitle("🛋️🛋️🛋️🛋️🛋️🛋️🛋️🛋️🛋️🛋️ espaço decorado");

        assertThat(value.codePointCount(0, value.length())).isEqualTo(WhatsAppPayloadConstraints.LIST_ROW_TITLE_LIMIT);
        assertThat(value).endsWith("...");
    }
}
