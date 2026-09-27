package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * Local isolation sink for the official webhook.
 *
 * It acknowledges the inbound boundary without invoking Hermes, AI, WhatsApp,
 * SMTP, or any other outbound adapter. This is deliberately not a conversation
 * processor; it exists so an isolated target can exercise ingress safely.
 */
@Component
@ConditionalOnProperty(name = "webhook.inbox.worker.enabled", havingValue = "true")
public final class InboundOnlyWebhookMessageHandler implements WebhookInbox {
    private static final Logger LOGGER = LoggerFactory.getLogger(InboundOnlyWebhookMessageHandler.class);

    @Override
    public void accept(InboundWhatsAppMessage message, Instant receivedAt) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(receivedAt, "receivedAt");
        LOGGER.info("Webhook inbound-only aceito: messageType={} receivedAt={}",
                message.messageType(), receivedAt);
    }
}
