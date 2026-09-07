package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase;
import br.com.urbana.connect.domain.reception.model.DeliveryFailureClassifier;
import br.com.urbana.connect.domain.reception.model.DeliveryFailureDisposition;
import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.port.out.DeliveryChannelGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDispatchException;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Processes committed outbox intents outside their originating transaction.
 * A successful state is written only with the provider's message identifier.
 */
public final class DeliveryOutboxWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryOutboxWorker.class);

    private final DeliveryOutboxGateway outbox;
    private final DeliveryDestinationGateway destinations;
    private final DeliveryChannelGateway channel;
    private final Clock clock;
    private final Duration retryBaseDelay;
    private final Duration recoveryLease;
    private final int maxAttempts;
    private final int batchSize;

    public DeliveryOutboxWorker(DeliveryOutboxGateway outbox,
                                DeliveryDestinationGateway destinations,
                                DeliveryChannelGateway channel,
                                Clock clock,
                                Duration retryBaseDelay,
                                int maxAttempts,
                                int batchSize) {
        this(outbox, destinations, channel, clock, retryBaseDelay,
                retryBaseDelay.multipliedBy(3), maxAttempts, batchSize);
    }

    public DeliveryOutboxWorker(DeliveryOutboxGateway outbox,
                                DeliveryDestinationGateway destinations,
                                DeliveryChannelGateway channel,
                                Clock clock,
                                Duration retryBaseDelay,
                                Duration recoveryLease,
                                int maxAttempts,
                                int batchSize) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.destinations = Objects.requireNonNull(destinations, "destinations");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retryBaseDelay = positive(retryBaseDelay, "retryBaseDelay");
        this.recoveryLease = positive(recoveryLease, "recoveryLease");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be at least one");
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be at least one");
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${delivery.outbox.fixed-delay:5s}")
    public void scheduledRun() {
        runOnce();
    }

    /** Returns the number of entries claimed by this worker invocation. */
    public int runOnce() {
        Instant now = clock.instant();
        int recovered;
        try {
            recovered = outbox.recoverStale(now, recoveryLease, batchSize);
        } catch (RuntimeException failure) {
            // A persistence outage must not turn an old SENDING claim into a
            // blind resend. Leave it for reconciliation and expose the fault.
            LOGGER.error("Não foi possível recuperar claims antigos da outbox", failure);
            recovered = 0;
        }
        if (recovered > 0) {
            LOGGER.warn("Outbox claims recovered as ambiguous after worker restart: count={}", recovered);
        }
        List<DeliveryOutbox> due;
        try {
            due = outbox.findDue(now, batchSize);
        } catch (RuntimeException failure) {
            LOGGER.error("Não foi possível consultar a outbox de entrega", failure);
            return 0;
        }
        int claimed = 0;
        for (DeliveryOutbox candidate : due) {
            Optional<DeliveryOutbox> lease;
            try {
                lease = outbox.claim(candidate.eventKey(), now);
            } catch (RuntimeException failure) {
                LOGGER.error("Não foi possível reivindicar item da outbox: eventKey={}",
                        candidate.eventKey(), failure);
                continue;
            }
            if (lease.isEmpty()) continue;
            claimed++;
            dispatch(lease.orElseThrow(), now);
        }
        return claimed;
    }

    private void dispatch(DeliveryOutbox claimed, Instant now) {
        Optional<String> destination;
        try {
            destination = Optional.ofNullable(destinations.resolve(claimed)).orElseGet(Optional::empty);
        } catch (RuntimeException failure) {
            markPreDispatchFailure(claimed, now, "destination resolver failure", failure);
            return;
        }
        if (destination.isEmpty() || destination.orElseThrow().isBlank()) {
            // The intent cannot reach a channel. It must be made visible to an
            // operator, never retried against an invented/default recipient.
            persist(claimed.deadLetter(now));
            LOGGER.error("Outbox dead-lettered because destination is unresolved: eventKey={} correlationId={}",
                    claimed.eventKey(), claimed.correlationId());
            return;
        }

        try {
            DeliveryChannelGateway.DeliveryResult result = channel.send(claimed, destination.orElseThrow());
            if (result == null || result.providerMessageId() == null || result.providerMessageId().isBlank()) {
                markAmbiguous(claimed, now, "provider did not return a message id", null);
                return;
            }
        persist(claimed.sent(result.providerMessageId(), now));
        } catch (DeliveryDispatchException failure) {
            if (DeliveryFailureClassifier.classify(failure.phase())
                    == DeliveryFailureDisposition.RETRY_IDEMPOTENTLY) {
                markPreDispatchFailure(claimed, now, failure.getMessage(), failure);
            } else {
                markAmbiguous(claimed, now, failure.getMessage(), failure);
            }
        } catch (RuntimeException failure) {
            // An adapter that cannot identify the phase is conservative: the
            // provider may have received it, so it requires reconciliation.
            markAmbiguous(claimed, now, "unknown delivery failure", failure);
        }
    }

    private void markPreDispatchFailure(DeliveryOutbox claimed, Instant now, String message, Exception failure) {
        DeliveryOutbox next = claimed.attempts() >= maxAttempts
                ? claimed.deadLetter(now)
                : claimed.retryable(nextRetryAt(claimed, now));
        persist(next);
        LOGGER.warn("Outbox pre-dispatch failure: eventKey={} disposition={} attempts={} message={}",
                claimed.eventKey(), next.status(), claimed.attempts(), message, failure);
    }

    private void markAmbiguous(DeliveryOutbox claimed, Instant now, String message, Exception failure) {
        persist(claimed.ambiguous(now));
        LOGGER.warn("Outbox delivery requires reconciliation: eventKey={} disposition={} message={}",
                claimed.eventKey(), DeliveryFailureClassifier.classify(DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN),
                message, failure);
    }

    private Instant nextRetryAt(DeliveryOutbox claimed, Instant now) {
        int exponent = Math.min(Math.max(claimed.attempts() - 1, 0), 8);
        return now.plus(retryBaseDelay.multipliedBy(1L << exponent));
    }

    private void persist(DeliveryOutbox next) {
        try {
            outbox.update(next);
        } catch (RuntimeException failure) {
            // Keep the provider outcome conservative: a missing state write
            // is never reported as SENT and will be surfaced for operations.
            LOGGER.error("Não foi possível persistir estado da outbox: eventKey={} status={}",
                    next.eventKey(), next.status(), failure);
        }
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
