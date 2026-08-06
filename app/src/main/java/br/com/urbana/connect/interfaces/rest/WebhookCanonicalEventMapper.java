package br.com.urbana.connect.interfaces.rest;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import br.com.urbana.connect.application.reception.InboundConversationEvent;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Pure edge mapper. Production webhook routing is not changed by the POC;
 * when it is adopted, this mapper gives it the exact simulator contract.
 */
public final class WebhookCanonicalEventMapper {
    private WebhookCanonicalEventMapper() {
    }

    public static InboundConversationEvent fromWhatsApp(InboundWhatsAppMessage message, Instant occurredAt) {
        if (message == null) throw new IllegalArgumentException("message is required");
        if (message.phoneNumber() == null || message.phoneNumber().isBlank()) {
            throw new IllegalArgumentException("phone number is required at the channel boundary");
        }
        String normalizedType = message.messageType() == null ? "text" : message.messageType().toLowerCase(Locale.ROOT);
        ReceptionMessageType type = switch (normalizedType) {
            case "audio", "voice" -> ReceptionMessageType.AUDIO;
            case "image", "photo" -> ReceptionMessageType.IMAGE;
            case "document", "file" -> ReceptionMessageType.DOCUMENT;
            case "button_reply", "list_reply", "interactive" -> ReceptionMessageType.INTERACTIVE;
            case "payment_proof" -> ReceptionMessageType.PAYMENT_PROOF;
            default -> ReceptionMessageType.TEXT;
        };
        String text = message.textBody();
        String media = type == ReceptionMessageType.IMAGE || type == ReceptionMessageType.DOCUMENT
                || type == ReceptionMessageType.AUDIO ? message.providerMessageId() : null;
        String transcript = type == ReceptionMessageType.AUDIO ? text : null;
        return new InboundConversationEvent(
                nonBlank(message.providerMessageId(), "whatsapp-event-" + digest(message.phoneNumber() + ":" + occurredAt)),
                "wa:" + digest(message.phoneNumber()), type,
                type == ReceptionMessageType.AUDIO ? null : text, transcript, media,
                message.interactiveReplyId(), occurredAt, message.providerMessageId());
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
