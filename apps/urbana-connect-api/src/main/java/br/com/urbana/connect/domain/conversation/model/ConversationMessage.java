package br.com.urbana.connect.domain.conversation.model;

import java.time.Instant;

public record ConversationMessage(
        String id,
        String conversationId,
        String phoneNumber,
        String channel,
        ConversationMessageDirection direction,
        ConversationMessageSenderType senderType,
        ConversationMessageType messageType,
        String rawText,
        String interactiveReplyId,
        String providerMessageId,
        Instant createdAt,
        String stepAtTime) {

    public static ConversationMessage inbound(
            String conversationId,
            String phoneNumber,
            ConversationMessageType messageType,
            String rawText,
            String interactiveReplyId,
            String providerMessageId,
            Instant createdAt,
            String stepAtTime) {
        return new ConversationMessage(
            null,
            conversationId,
            phoneNumber,
            "WHATSAPP",
            ConversationMessageDirection.INBOUND,
            ConversationMessageSenderType.USER,
            messageType,
            rawText,
            interactiveReplyId,
            providerMessageId,
            createdAt,
            stepAtTime
        );
    }

    public static ConversationMessage outbound(
            String conversationId,
            String phoneNumber,
            ConversationMessageType messageType,
            String rawText,
            Instant createdAt,
            String stepAtTime) {
        return new ConversationMessage(
            null,
            conversationId,
            phoneNumber,
            "WHATSAPP",
            ConversationMessageDirection.OUTBOUND,
            ConversationMessageSenderType.URBA_BOT,
            messageType,
            rawText,
            null,
            null,
            createdAt,
            stepAtTime
        );
    }
}
