package br.com.urbana.connect.domain.reception.model;

/** Immutable evidence points emitted by the web-consent lifecycle. */
public enum TermsConsentAuditEventType {
    ISSUED,
    PAGE_PRESENTED,
    END_REACHED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    INVALIDATED
}
