package br.com.urbana.connect.domain.reception.model;

/** Required operational action after a failed outbound attempt. */
public enum DeliveryFailureDisposition {
    RETRY_IDEMPOTENTLY,
    RECONCILE_BEFORE_RETRY
}
