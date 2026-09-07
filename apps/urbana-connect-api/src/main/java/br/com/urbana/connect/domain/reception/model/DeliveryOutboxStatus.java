package br.com.urbana.connect.domain.reception.model;

public enum DeliveryOutboxStatus {
    PENDING,
    SENDING,
    SENT,
    RETRYABLE,
    AMBIGUOUS,
    DEAD_LETTER
}
