package br.com.urbana.connect.application.reception;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PocReceptionBatchFlushSchedulerTest {
    @Test
    void flushesOnlyDueBatchesUsingTheConfiguredClock() {
        PocReceptionIngress ingress = mock(PocReceptionIngress.class);
        Instant now = Instant.parse("2026-08-05T12:00:10Z");
        PocReceptionBatchFlushScheduler scheduler = new PocReceptionBatchFlushScheduler(
                ingress, Clock.fixed(now, ZoneOffset.UTC));

        scheduler.flushDueBatches();

        verify(ingress).flushAllDue(now);
    }
}
