package br.com.urbana.connect.domain.conversation.model;

public record ConversationContext(String paymentMethod) {

    public static ConversationContext empty() {
        return new ConversationContext(null);
    }
}
