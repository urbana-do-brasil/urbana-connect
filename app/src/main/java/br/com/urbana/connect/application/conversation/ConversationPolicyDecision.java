package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;

public record ConversationPolicyDecision(
        ConversationPolicyDecisionType type,
        Conversation updatedConversation,
        ConversationalAiReply reply,
        String reason) {
}
