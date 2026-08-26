package br.com.urbana.connect.domain.reception.model;

/** Durable ownership-return state; HUMAN remains fail-closed until completion. */
public enum ResumeStatus {
    NONE,
    PENDING,
    SYNCHRONIZING,
    DECIDING,
    COMPLETED,
    RETURNED_TO_HUMAN,
    FAILED_SAFE
}
