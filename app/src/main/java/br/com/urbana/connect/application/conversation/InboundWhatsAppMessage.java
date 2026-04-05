package br.com.urbana.connect.application.conversation;

public record InboundWhatsAppMessage(
        String phoneNumber,
        String textBody,
        String interactiveReplyId) {
}
