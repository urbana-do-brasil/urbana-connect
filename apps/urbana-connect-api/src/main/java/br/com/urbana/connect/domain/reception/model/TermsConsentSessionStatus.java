package br.com.urbana.connect.domain.reception.model;

/**
 * State of an ephemeral web-consent presentation.  The state is deliberately
 * separate from the append-only audit events: it is the projection used to
 * authorize the next request and may be removed by Mongo TTL after expiry.
 */
public enum TermsConsentSessionStatus {
    ISSUED,
    PAGE_PRESENTED,
    END_REACHED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    INVALIDATED
}
