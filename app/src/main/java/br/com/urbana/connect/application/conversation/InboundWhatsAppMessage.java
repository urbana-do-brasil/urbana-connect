package br.com.urbana.connect.application.conversation;

public record InboundWhatsAppMessage(
        String phoneNumber,
        String textBody,
        String interactiveReplyId,
        String interactiveReplyTitle,
        String messageType,
        String providerMessageId) {

    public InboundWhatsAppMessage(String phoneNumber, String textBody, String interactiveReplyId) {
        this(phoneNumber, textBody, interactiveReplyId, "", "text", "");
    }
}
