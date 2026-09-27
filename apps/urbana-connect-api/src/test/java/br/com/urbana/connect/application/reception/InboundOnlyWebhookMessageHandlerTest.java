package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class InboundOnlyWebhookMessageHandlerTest {

    @Test
    void acceptsSyntheticInboundAndLogsOnlyNonSensitiveMetadata(CapturedOutput output) {
        var handler = new InboundOnlyWebhookMessageHandler();
        var phoneNumber = "+15550000001";
        var body = "synthetic-body-must-not-be-logged";
        var providerMessageId = "wamid-provider-id-must-not-be-logged-in-full";
        var message = new InboundWhatsAppMessage(
                phoneNumber,
                body,
                "",
                "",
                "text",
                providerMessageId);
        var receivedAt = Instant.parse("2026-09-27T12:00:00Z");

        handler.accept(message, receivedAt);

        assertThat(output.getOut())
                .contains("Webhook inbound-only aceito: messageType=text receivedAt=2026-09-27T12:00:00Z")
                .doesNotContain(providerMessageId, phoneNumber, body);
    }
}
