package br.com.urbana.connect.domain.conversation.model;

public enum ConversationStep {
    GREETING,
    TRIAGE_GUIDED,
    TRIAGE_DIRECT,
    AWAITING_CONFIRMATION,
    AWAITING_TERMS,
    AWAITING_PAYMENT_METHOD,
    PAYMENT_LINK_SENT
}
