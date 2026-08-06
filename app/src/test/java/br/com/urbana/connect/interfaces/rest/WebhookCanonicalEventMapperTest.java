package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import br.com.urbana.connect.application.reception.InboundConversationEvent;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookCanonicalEventMapperTest {
    @Test
    void mapsWhatsAppTextAndInteractiveMessagesToTheSameCanonicalContract() {
        InboundConversationEvent text = WebhookCanonicalEventMapper.fromWhatsApp(
                new InboundWhatsAppMessage("5511999999999", "Oi", "", "", "text", "wa-1"),
                Instant.parse("2026-08-05T12:00:00Z"));
        InboundConversationEvent interactive = WebhookCanonicalEventMapper.fromWhatsApp(
                new InboundWhatsAppMessage("5511999999999", "", "service.decor", "Decor", "button_reply", "wa-2"),
                Instant.parse("2026-08-05T12:00:01Z"));

        assertThat(text.contactId()).startsWith("wa:").doesNotContain("5511999999999");
        assertThat(text.type()).isEqualTo(ReceptionMessageType.TEXT);
        assertThat(text.text()).isEqualTo("Oi");
        assertThat(interactive.contactId()).isEqualTo(text.contactId());
        assertThat(interactive.type()).isEqualTo(ReceptionMessageType.INTERACTIVE);
        assertThat(interactive.interactiveReplyId()).isEqualTo("service.decor");
    }
}
