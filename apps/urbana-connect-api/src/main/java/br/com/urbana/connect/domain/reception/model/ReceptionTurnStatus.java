package br.com.urbana.connect.domain.reception.model;

public enum ReceptionTurnStatus {
    QUEUED,
    RUNNING,
    DELAYED,
    RECONCILING,
    COMPLETED,
    FAILED_SAFE_TO_RETRY,
    FAILED_TERMINAL,
    /** @deprecated use FAILED_SAFE_TO_RETRY or FAILED_TERMINAL. */
    @Deprecated
    FAILED,
    BLOCKED_BY_HUMAN
}
