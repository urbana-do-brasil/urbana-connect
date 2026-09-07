package br.com.urbana.connect.domain.reception.model;

/** The last point known by the application when an outbound attempt failed. */
public enum DeliveryDispatchPhase {
    /** The provider was not invoked, so an idempotent retry is safe. */
    PRE_DISPATCH,
    /** The provider may have accepted the request; reconcile before any resend. */
    POST_DISPATCH_UNKNOWN
}
