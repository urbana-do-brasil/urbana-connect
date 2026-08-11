package br.com.urbana.connect.application.reception;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceptionTurnCoordinatorTest {
    @Test
    void serializesConcurrentOperationsForOneContact() throws Exception {
        ReceptionTurnCoordinator coordinator = new ReceptionTurnCoordinator();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> coordinator.serialize("contact-1", () -> {
            maxActive.updateAndGet(previous -> Math.max(previous, active.incrementAndGet()));
            entered.countDown();
            try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            active.decrementAndGet();
            return "first";
        }));
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        var second = pool.submit(() -> coordinator.serialize("contact-1", () -> {
            maxActive.updateAndGet(previous -> Math.max(previous, active.incrementAndGet()));
            active.decrementAndGet();
            return "second";
        }));
        Thread.sleep(100);
        assertThat(maxActive).hasValue(1);
        release.countDown();
        assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo("second");
        pool.shutdownNow();
    }

    @Test
    void duplicateEventRunsOnlyOnceAndReturnsOriginalReceipt() {
        ReceptionTurnCoordinator coordinator = new ReceptionTurnCoordinator();
        AtomicInteger executions = new AtomicInteger();

        ReceptionTurnCoordinator.ExecutionResult<String> first = coordinator.execute("contact-1", "event-1",
                () -> "receipt-" + executions.incrementAndGet());
        ReceptionTurnCoordinator.ExecutionResult<String> duplicate = coordinator.execute("contact-1", "event-1",
                () -> "receipt-" + executions.incrementAndGet());

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.value()).isEqualTo("receipt-1");
        assertThat(executions).hasValue(1);
    }

    @Test
    void neverRunsTwoOperationsForOneContactDuringLockMapChurn() throws Exception {
        ReceptionTurnCoordinator coordinator = new ReceptionTurnCoordinator();
        var pool = Executors.newFixedThreadPool(8);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int round = 0; round < 250; round++) {
            CountDownLatch ready = new CountDownLatch(8);
            CountDownLatch start = new CountDownLatch(1);
            for (int task = 0; task < 8; task++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
                    coordinator.serialize("contact-race", () -> {
                        int running = active.incrementAndGet();
                        maxActive.updateAndGet(previous -> Math.max(previous, running));
                        Thread.yield();
                        active.decrementAndGet();
                        return null;
                    });
                    return null;
                }));
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        }

        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(maxActive).hasValue(1);
    }
}
