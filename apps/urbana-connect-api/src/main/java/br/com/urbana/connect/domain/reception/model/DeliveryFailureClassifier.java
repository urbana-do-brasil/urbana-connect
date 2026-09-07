package br.com.urbana.connect.domain.reception.model;

import java.util.Objects;

/**
 * Keeps retry policy explicit: a timeout after dispatch is never treated as a
 * successful delivery and is never blindly resent.
 */
public final class DeliveryFailureClassifier {
    private DeliveryFailureClassifier() {
    }

    public static DeliveryFailureDisposition classify(DeliveryDispatchPhase phase) {
        return switch (Objects.requireNonNull(phase, "phase")) {
            case PRE_DISPATCH -> DeliveryFailureDisposition.RETRY_IDEMPOTENTLY;
            case POST_DISPATCH_UNKNOWN -> DeliveryFailureDisposition.RECONCILE_BEFORE_RETRY;
        };
    }

    public static DeliveryOutboxStatus outboxStatus(DeliveryDispatchPhase phase) {
        return switch (classify(phase)) {
            case RETRY_IDEMPOTENTLY -> DeliveryOutboxStatus.RETRYABLE;
            case RECONCILE_BEFORE_RETRY -> DeliveryOutboxStatus.AMBIGUOUS;
        };
    }
}
