package br.com.urbana.connect.domain.conversation.model;

public enum ConversationSlotLevel {
    UNKNOWN,
    TENTATIVE,
    CONFIRMED;

    public boolean satisfies(ConversationSlotLevel requiredLevel) {
        if (requiredLevel == null || requiredLevel == UNKNOWN) {
            return true;
        }
        if (this == CONFIRMED) {
            return true;
        }
        return this == requiredLevel;
    }
}
