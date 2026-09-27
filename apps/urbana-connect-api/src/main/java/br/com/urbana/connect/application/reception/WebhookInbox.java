package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;

import java.time.Instant;

/** Inbound boundary used by isolated targets that must not process or deliver messages. */
@FunctionalInterface
public interface WebhookInbox {
    void accept(InboundWhatsAppMessage message, Instant receivedAt);
}
