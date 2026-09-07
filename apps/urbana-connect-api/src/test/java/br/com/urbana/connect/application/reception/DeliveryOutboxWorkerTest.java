package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase;
import br.com.urbana.connect.domain.reception.model.DeliveryOutbox;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxKind;
import br.com.urbana.connect.domain.reception.model.DeliveryOutboxStatus;
import br.com.urbana.connect.domain.reception.port.out.DeliveryChannelGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationGateway;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDispatchException;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryOutboxWorkerTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void marksSentOnlyWhenTheProviderReturnsItsMessageId() {
        MemoryOutbox outbox = new MemoryOutbox(pending("send"));
        DeliveryOutboxWorker worker = worker(outbox, item -> Optional.of("resolved-destination"),
                (item, destination) -> new DeliveryChannelGateway.DeliveryResult("wamid.out.1"), 3);

        assertThat(worker.runOnce()).isEqualTo(1);

        DeliveryOutbox saved = outbox.get("send");
        assertThat(saved.status()).isEqualTo(DeliveryOutboxStatus.SENT);
        assertThat(saved.providerMessageId()).isEqualTo("wamid.out.1");
        assertThat(saved.attempts()).isEqualTo(1);
        assertThat(saved.sentAt()).isEqualTo(NOW);
    }

    @Test
    void retriesOnlyKnownPreDispatchFailures() {
        MemoryOutbox outbox = new MemoryOutbox(pending("retry"));
        DeliveryOutboxWorker worker = worker(outbox, item -> Optional.of("resolved"),
                (item, destination) -> {
                    throw new DeliveryDispatchException("connection refused", DeliveryDispatchPhase.PRE_DISPATCH);
                }, 3);

        worker.runOnce();

        DeliveryOutbox saved = outbox.get("retry");
        assertThat(saved.status()).isEqualTo(DeliveryOutboxStatus.RETRYABLE);
        assertThat(saved.attempts()).isEqualTo(1);
        assertThat(saved.nextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void marksPostDispatchUncertaintyAsAmbiguousWithoutResending() {
        MemoryOutbox outbox = new MemoryOutbox(pending("ambiguous"));
        DeliveryOutboxWorker worker = worker(outbox, item -> Optional.of("resolved"),
                (item, destination) -> {
                    throw new DeliveryDispatchException("provider timeout", DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN);
                }, 3);

        worker.runOnce();

        DeliveryOutbox saved = outbox.get("ambiguous");
        assertThat(saved.status()).isEqualTo(DeliveryOutboxStatus.AMBIGUOUS);
        assertThat(saved.providerMessageId()).isNull();
        assertThat(saved.attempts()).isEqualTo(1);
    }

    @Test
    void deadLettersPreDispatchFailureAfterTheConfiguredAttemptLimit() {
        DeliveryOutbox alreadyRetried = new DeliveryOutbox("limited", DeliveryOutboxKind.WHATSAPP_MENU,
                "opaque-ref", "payload", DeliveryOutboxStatus.RETRYABLE, 1, NOW, null,
                "corr-limited", NOW.minusSeconds(20), null);
        MemoryOutbox outbox = new MemoryOutbox(alreadyRetried);
        DeliveryOutboxWorker worker = worker(outbox, item -> Optional.of("resolved"),
                (item, destination) -> {
                    throw new DeliveryDispatchException("unavailable", DeliveryDispatchPhase.PRE_DISPATCH);
                }, 2);

        worker.runOnce();

        DeliveryOutbox saved = outbox.get("limited");
        assertThat(saved.status()).isEqualTo(DeliveryOutboxStatus.DEAD_LETTER);
        assertThat(saved.attempts()).isEqualTo(2);
    }

    @Test
    void deadLettersUnresolvedDestinationsWithoutCallingAnyChannel() {
        MemoryOutbox outbox = new MemoryOutbox(pending("unresolved"));
        boolean[] channelCalled = {false};
        DeliveryOutboxWorker worker = worker(outbox, item -> Optional.empty(), (item, destination) -> {
            channelCalled[0] = true;
            return new DeliveryChannelGateway.DeliveryResult("must-not-happen");
        }, 3);

        worker.runOnce();

        assertThat(outbox.get("unresolved").status()).isEqualTo(DeliveryOutboxStatus.DEAD_LETTER);
        assertThat(channelCalled[0]).isFalse();
    }

    @Test
    void treatsProviderResponsesWithoutAnIdAsAmbiguousInsteadOfSuccessful() {
        MemoryOutbox outbox = new MemoryOutbox(pending("missing-id"));
        DeliveryOutboxWorker worker = worker(outbox, item -> Optional.of("resolved"),
                (item, destination) -> new DeliveryChannelGateway.DeliveryResult(null), 3);

        worker.runOnce();

        assertThat(outbox.get("missing-id").status()).isEqualTo(DeliveryOutboxStatus.AMBIGUOUS);
    }

    @Test
    void recoversAStaleClaimAsAmbiguousAfterAProcessRestart() {
        DeliveryOutbox sending = new DeliveryOutbox("stale", DeliveryOutboxKind.WHATSAPP_MENU,
                "opaque-ref", "payload", DeliveryOutboxStatus.SENDING, 1,
                NOW.minusSeconds(90), null, "corr-stale", NOW.minusSeconds(120), null);
        MemoryOutbox outbox = new MemoryOutbox(sending);
        DeliveryOutboxWorker worker = new DeliveryOutboxWorker(outbox,
                item -> Optional.of("resolved"),
                (item, destination) -> new DeliveryChannelGateway.DeliveryResult("must-not-send"),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(10), Duration.ofSeconds(30), 3, 10);

        assertThat(worker.runOnce()).isZero();
        assertThat(outbox.get("stale").status()).isEqualTo(DeliveryOutboxStatus.AMBIGUOUS);
    }

    private static DeliveryOutboxWorker worker(MemoryOutbox outbox, DeliveryDestinationGateway destinations,
                                               DeliveryChannelGateway channel, int maxAttempts) {
        return new DeliveryOutboxWorker(outbox, destinations, channel,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(10), maxAttempts, 10);
    }

    private static DeliveryOutbox pending(String key) {
        return DeliveryOutbox.pending(key, DeliveryOutboxKind.WHATSAPP_MENU,
                "opaque-ref", "payload", "corr-" + key, NOW);
    }

    private static final class MemoryOutbox implements DeliveryOutboxGateway {
        private final Map<String, DeliveryOutbox> values = new LinkedHashMap<>();

        private MemoryOutbox(DeliveryOutbox initial) {
            values.put(initial.eventKey(), initial);
        }

        @Override
        public DeliveryOutbox saveIfAbsent(DeliveryOutbox value) {
            return values.computeIfAbsent(value.eventKey(), ignored -> value);
        }

        @Override
        public Optional<DeliveryOutbox> findByEventKey(String eventKey) {
            return Optional.ofNullable(values.get(eventKey));
        }

        @Override
        public List<DeliveryOutbox> findDue(Instant now, int limit) {
            return values.values().stream()
                    .filter(value -> (value.status() == DeliveryOutboxStatus.PENDING
                            || value.status() == DeliveryOutboxStatus.RETRYABLE)
                            && !value.nextAttemptAt().isAfter(now))
                    .limit(limit).toList();
        }

        @Override
        public Optional<DeliveryOutbox> claim(String eventKey, Instant now) {
            DeliveryOutbox value = values.get(eventKey);
            if (value == null || (value.status() != DeliveryOutboxStatus.PENDING
                    && value.status() != DeliveryOutboxStatus.RETRYABLE)) {
                return Optional.empty();
            }
            DeliveryOutbox claimed = value.sending(now);
            values.put(eventKey, claimed);
            return Optional.of(claimed);
        }

        @Override
        public int recoverStale(Instant now, Duration lease, int limit) {
            int recovered = 0;
            for (DeliveryOutbox value : List.copyOf(values.values())) {
                if (recovered >= limit || value.status() != DeliveryOutboxStatus.SENDING
                        || value.nextAttemptAt().isAfter(now.minus(lease))) {
                    continue;
                }
                values.put(value.eventKey(), value.ambiguous(now));
                recovered++;
            }
            return recovered;
        }

        @Override
        public DeliveryOutbox update(DeliveryOutbox value) {
            values.put(value.eventKey(), value);
            return value;
        }

        private DeliveryOutbox get(String key) {
            return values.get(key);
        }
    }
}
