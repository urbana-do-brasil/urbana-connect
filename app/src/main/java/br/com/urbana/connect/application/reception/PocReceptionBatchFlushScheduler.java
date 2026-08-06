package br.com.urbana.connect.application.reception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.Objects;

/** Flushes due POC text batches without changing the batching window semantics. */
public final class PocReceptionBatchFlushScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PocReceptionBatchFlushScheduler.class);

    private final PocReceptionIngress ingress;
    private final Clock clock;

    public PocReceptionBatchFlushScheduler(PocReceptionIngress ingress, Clock clock) {
        this.ingress = Objects.requireNonNull(ingress, "ingress");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString = "${hermes.poc.batch-flush-interval:1s}")
    public void flushDueBatches() {
        try {
            ingress.flushAllDue(clock.instant());
        } catch (RuntimeException exception) {
            // A scheduler thread must survive one malformed or transient batch.
            LOGGER.warn("POC batch flush failed; the next scheduled flush will continue", exception);
        }
    }
}
