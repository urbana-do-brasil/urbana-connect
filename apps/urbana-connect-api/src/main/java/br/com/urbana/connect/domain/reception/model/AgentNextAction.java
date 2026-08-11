package br.com.urbana.connect.domain.reception.model;

public enum AgentNextAction {
    NONE,
    AWAIT_CUSTOMER,
    AWAIT_PAYMENT_PROOF,
    AWAIT_PAYMENT_APPROVAL,
    HANDOFF
}
