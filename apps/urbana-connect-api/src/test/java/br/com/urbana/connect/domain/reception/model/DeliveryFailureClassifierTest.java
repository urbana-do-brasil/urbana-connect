package br.com.urbana.connect.domain.reception.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryFailureClassifierTest {

    @Test
    void allowsIdempotentRetryOnlyWhenTheProviderWasNotCalled() {
        assertThat(DeliveryFailureClassifier.classify(DeliveryDispatchPhase.PRE_DISPATCH))
                .isEqualTo(DeliveryFailureDisposition.RETRY_IDEMPOTENTLY);
        assertThat(DeliveryFailureClassifier.outboxStatus(DeliveryDispatchPhase.PRE_DISPATCH))
                .isEqualTo(DeliveryOutboxStatus.RETRYABLE);
    }

    @Test
    void requiresReconciliationWhenTheProviderMayHaveReceivedTheRequest() {
        assertThat(DeliveryFailureClassifier.classify(DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN))
                .isEqualTo(DeliveryFailureDisposition.RECONCILE_BEFORE_RETRY);
        assertThat(DeliveryFailureClassifier.outboxStatus(DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN))
                .isEqualTo(DeliveryOutboxStatus.AMBIGUOUS);
    }
}
