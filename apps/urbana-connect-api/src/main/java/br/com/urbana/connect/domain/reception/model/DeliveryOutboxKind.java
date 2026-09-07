package br.com.urbana.connect.domain.reception.model;

/** Effects that may be delivered after a domain transaction commits. */
public enum DeliveryOutboxKind {
    WHATSAPP_TERMS_LINK,
    WHATSAPP_PAYMENT_OPTIONS,
    WHATSAPP_MENU,
    WHATSAPP_HERMES_REPLY,
    WHATSAPP_HUMAN_HANDOFF_ACK,
    /**
     * Durable request for the support mailbox.  It is intentionally an intent,
     * not an assertion that an email provider has accepted the message.
     */
    EMAIL_HUMAN_HANDOFF
}
