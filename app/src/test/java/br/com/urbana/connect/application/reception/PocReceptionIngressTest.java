package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PocReceptionIngressTest {
    private static final Instant START = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void batchesFragmentsAndNormalizesAudioBeforeCallingTheOrchestrator() {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            List<InboundConversationEvent> batch = invocation.getArgument(0);
            return new ReceptionOrchestrator.TurnReceipt(batch.getLast().eventId(), "corr-" + batch.getLast().eventId(),
                    ReceptionOrchestrator.TurnStatus.COMPLETED,
                    new AgentOutput("ok", AgentNextAction.AWAIT_CUSTOMER), null);
        });
        PocReceptionIngress ingress = new PocReceptionIngress(orchestrator, new MessageBatcher(),
                new MediaNormalizationService(media -> Optional.of("transcrição da sala")));

        assertThat(ingress.accept(text("fragment-1", "Quero", START)).status())
                .isEqualTo(ReceptionOrchestrator.TurnStatus.QUEUED);
        assertThat(ingress.accept(text("fragment-2", "decorar a sala", START.plusSeconds(3))).status())
                .isEqualTo(ReceptionOrchestrator.TurnStatus.QUEUED);
        verify(orchestrator, never()).processBatch(anyList());

        ReceptionOrchestrator.TurnReceipt queuedAudio = ingress.accept(new InboundConversationEvent(
                "audio-1", "poc:ana", ReceptionMessageType.AUDIO, null, null,
                "poc/audio-room.txt", null, START.plusSeconds(15), null));

        assertThat(queuedAudio.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.QUEUED);
        verify(orchestrator).processBatch(argThat(batch -> batch.size() == 2
                && batch.getFirst().eventId().equals("fragment-1")
                && batch.getLast().eventId().equals("fragment-2")));

        ingress.flushDue("poc:ana", START.plusSeconds(19));

        verify(orchestrator).processBatch(argThat(batch -> batch.size() == 1
                && batch.getFirst().type() == ReceptionMessageType.AUDIO
                && batch.getFirst().transcript().equals("transcrição da sala")
                && batch.getFirst().mediaFixture().equals("poc/audio-room.txt")));
    }

    @Test
    void releasesImageAndPaymentProofThroughIngressWithoutAutomaticApproval() {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            List<InboundConversationEvent> batch = invocation.getArgument(0);
            return new ReceptionOrchestrator.TurnReceipt(batch.getFirst().eventId(), "corr",
                    ReceptionOrchestrator.TurnStatus.COMPLETED,
                    new AgentOutput("aguardando", AgentNextAction.AWAIT_PAYMENT_APPROVAL), null);
        });
        PocReceptionIngress ingress = new PocReceptionIngress(orchestrator, new MessageBatcher(),
                new MediaNormalizationService(media -> Optional.empty()));

        ReceptionOrchestrator.TurnReceipt image = ingress.accept(new InboundConversationEvent(
                "image-1", "poc:ana", ReceptionMessageType.IMAGE, null, null,
                "poc/environment-image.txt", null, START, null));
        assertThat(image.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.QUEUED);
        ingress.flushDue("poc:ana", START.plusSeconds(4));

        ReceptionOrchestrator.TurnReceipt proof = ingress.accept(new InboundConversationEvent(
                "proof-1", "poc:ana", ReceptionMessageType.PAYMENT_PROOF, null, null,
                "poc/payment-proof-fixture.svg", null, START.plusSeconds(5), null));

        assertThat(proof.status()).isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        verify(orchestrator).processBatch(argThat(batch -> batch.size() == 1
                && batch.getFirst().type() == ReceptionMessageType.PAYMENT_PROOF
                && batch.getFirst().isPaymentProof()));
        verify(orchestrator, never()).approvePaymentProof(anyString());
    }

    @Test
    void forceFlushReleasesNormalTextThroughTheOrchestrator() {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            List<InboundConversationEvent> batch = invocation.getArgument(0);
            return new ReceptionOrchestrator.TurnReceipt(batch.getFirst().eventId(), "corr-force",
                    ReceptionOrchestrator.TurnStatus.COMPLETED,
                    new AgentOutput("ok", AgentNextAction.AWAIT_CUSTOMER), null);
        });
        PocReceptionIngress ingress = new PocReceptionIngress(orchestrator, new MessageBatcher(),
                new MediaNormalizationService(media -> Optional.empty()));

        ingress.accept(text("force-1", "Oi", START));

        assertThat(ingress.forceFlush("poc:ana")).singleElement()
                .extracting(ReceptionOrchestrator.TurnReceipt::status)
                .isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        verify(orchestrator).processBatch(argThat(batch -> batch.size() == 1
                && batch.getFirst().eventId().equals("force-1")));
    }

    @Test
    void forceFlushWaitsForAConcurrentScheduledReleaseInsteadOfReturningAnEmptyList() throws Exception {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch allowProcessingToFinish = new CountDownLatch(1);
        when(orchestrator.processBatch(anyList())).thenAnswer(invocation -> {
            List<InboundConversationEvent> batch = invocation.getArgument(0);
            processingStarted.countDown();
            assertThat(allowProcessingToFinish.await(2, TimeUnit.SECONDS)).isTrue();
            return new ReceptionOrchestrator.TurnReceipt(batch.getFirst().eventId(), "corr-race",
                    ReceptionOrchestrator.TurnStatus.COMPLETED,
                    new AgentOutput("ok", AgentNextAction.AWAIT_CUSTOMER), null);
        });
        PocReceptionIngress ingress = new PocReceptionIngress(orchestrator, new MessageBatcher(),
                new MediaNormalizationService(media -> Optional.empty()));
        ingress.accept(text("race-1", "Oi", START));

        var executor = Executors.newFixedThreadPool(2);
        try {
            var scheduled = executor.submit(() -> ingress.flushDue("poc:ana", START.plusSeconds(4)));
            assertThat(processingStarted.await(1, TimeUnit.SECONDS)).isTrue();
            var forced = executor.submit(() -> ingress.forceFlush("poc:ana"));

            allowProcessingToFinish.countDown();

            assertThat(scheduled.get(2, TimeUnit.SECONDS)).singleElement()
                    .extracting(ReceptionOrchestrator.TurnReceipt::eventId)
                    .isEqualTo("race-1");
            assertThat(forced.get(2, TimeUnit.SECONDS)).singleElement()
                    .extracting(ReceptionOrchestrator.TurnReceipt::eventId)
                    .isEqualTo("race-1");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retriesOneRetryableTurnBeforeReturningItToTheSyntheticIngress() {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        ReceptionOrchestrator.TurnReceipt retryable = new ReceptionOrchestrator.TurnReceipt(
                "retry-1", "corr-retry", ReceptionOrchestrator.TurnStatus.FAILED_RETRYABLE,
                null, "temporary Hermes failure");
        ReceptionOrchestrator.TurnReceipt completed = new ReceptionOrchestrator.TurnReceipt(
                "retry-1", "corr-retry", ReceptionOrchestrator.TurnStatus.COMPLETED,
                new AgentOutput("recuperado", AgentNextAction.AWAIT_CUSTOMER), null);
        when(orchestrator.processBatch(anyList())).thenReturn(retryable, completed);
        PocReceptionIngress ingress = new PocReceptionIngress(orchestrator, new MessageBatcher(),
                new MediaNormalizationService(media -> Optional.empty()));

        ingress.accept(text("retry-1", "Oi", START));

        assertThat(ingress.forceFlush("poc:ana")).singleElement()
                .extracting(ReceptionOrchestrator.TurnReceipt::status)
                .isEqualTo(ReceptionOrchestrator.TurnStatus.COMPLETED);
        verify(orchestrator, times(2)).processBatch(anyList());
    }

    @Test
    void returnsFinalizedDuplicateBeforePuttingItBackInTheBatcher() {
        ReceptionOrchestrator orchestrator = mock(ReceptionOrchestrator.class);
        ReceptionOrchestrator.TurnReceipt duplicate = new ReceptionOrchestrator.TurnReceipt(
                "already-seen", "corr-existing", ReceptionOrchestrator.TurnStatus.DUPLICATE,
                new AgentOutput("já processado", AgentNextAction.AWAIT_CUSTOMER), null);
        when(orchestrator.duplicateReceiptIfFinalized(any())).thenReturn(Optional.of(duplicate));
        PocReceptionIngress ingress = new PocReceptionIngress(orchestrator, new MessageBatcher(),
                new MediaNormalizationService(media -> Optional.empty()));

        assertThat(ingress.accept(text("already-seen", "Oi novamente", START)))
                .isEqualTo(duplicate);
        verify(orchestrator).duplicateReceiptIfFinalized(any());
        verify(orchestrator, never()).processBatch(anyList());
    }

    private static InboundConversationEvent text(String id, String value, Instant at) {
        return new InboundConversationEvent(id, "poc:ana", ReceptionMessageType.TEXT,
                value, null, null, null, at, null);
    }
}
