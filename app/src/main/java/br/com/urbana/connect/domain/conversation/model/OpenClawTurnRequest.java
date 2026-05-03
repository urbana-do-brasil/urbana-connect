package br.com.urbana.connect.domain.conversation.model;

public record OpenClawTurnRequest(
        String sessionKey,
        String text,
        String from,
        String conversationId,
        String timestamp) {
}
