package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBatcherTest {
    private static final Instant START = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void groupsTextFragmentsWithinMovingWindowAndFlushesAfterFourSeconds() {
        MessageBatcher batcher = new MessageBatcher();

        assertThat(batcher.accept(text("e-1", "Oi", START)).readyBatches()).isEmpty();
        assertThat(batcher.accept(text("e-2", "preciso", START.plusSeconds(3))).readyBatches()).isEmpty();

        MessageBatcher.Release release = batcher.flushDue("poc:ana", START.plusSeconds(7));

        assertThat(release.readyBatches()).containsExactly(List.of(
                text("e-1", "Oi", START),
                text("e-2", "preciso", START.plusSeconds(3))));
    }

    @Test
    void capsAContinuousTextBatchAtTenSecondsFromFirstFragment() {
        MessageBatcher batcher = new MessageBatcher();
        batcher.accept(text("e-1", "um", START));
        batcher.accept(text("e-2", "dois", START.plusSeconds(4)));
        batcher.accept(text("e-3", "três", START.plusSeconds(8)));

        MessageBatcher.Release release = batcher.flushDue("poc:ana", START.plusSeconds(10));

        assertThat(release.readyBatches()).hasSize(1);
        assertThat(release.readyBatches().getFirst()).extracting(InboundConversationEvent::eventId)
                .containsExactly("e-1", "e-2", "e-3");
    }

    @Test
    void startsANewBatchWhenTheGapExceedsFourSeconds() {
        MessageBatcher batcher = new MessageBatcher();
        batcher.accept(text("e-1", "primeiro", START));

        MessageBatcher.Release release = batcher.accept(
                text("e-2", "segundo", START.plusSeconds(5)));

        assertThat(release.readyBatches()).containsExactly(List.of(text("e-1", "primeiro", START)));
        assertThat(batcher.flushDue("poc:ana", START.plusSeconds(9)).readyBatches())
                .containsExactly(List.of(text("e-2", "segundo", START.plusSeconds(5))));
        assertThat(batcher.flushDue("poc:ana", START.plusSeconds(10)).readyBatches()).isEmpty();
    }

    @Test
    void releasesInteractiveAndPaymentProofEventsImmediately() {
        MessageBatcher batcher = new MessageBatcher();
        batcher.accept(text("e-1", "aguarde", START));

        MessageBatcher.Release interactive = batcher.accept(new InboundConversationEvent(
                "e-2", "poc:ana", ReceptionMessageType.INTERACTIVE, "Decor", null,
                null, "service.decor", START.plusSeconds(1), null));

        assertThat(interactive.readyBatches()).containsExactly(
                List.of(text("e-1", "aguarde", START)),
                List.of(new InboundConversationEvent("e-2", "poc:ana", ReceptionMessageType.INTERACTIVE,
                        "Decor", null, null, "service.decor", START.plusSeconds(1), null)));

        MessageBatcher.Release proof = batcher.accept(new InboundConversationEvent(
                "e-3", "poc:ana", ReceptionMessageType.IMAGE, null, null,
                "poc/payment-proof-fixture.svg", null, START.plusSeconds(2), null));
        assertThat(proof.readyBatches()).containsExactly(List.of(
                new InboundConversationEvent("e-3", "poc:ana", ReceptionMessageType.IMAGE, null, null,
                        "poc/payment-proof-fixture.svg", null, START.plusSeconds(2), null)));
    }

    @Test
    void keepsContactsInIndependentBatches() {
        MessageBatcher batcher = new MessageBatcher();
        batcher.accept(text("ana-1", "Ana", START));
        batcher.accept(new InboundConversationEvent("bia-1", "poc:bia", ReceptionMessageType.TEXT,
                "Bia", null, null, null, START.plusSeconds(1), null));

        assertThat(batcher.flushDue("poc:ana", START.plusSeconds(3)).readyBatches()).isEmpty();
        assertThat(batcher.flushDue("poc:bia", START.plusSeconds(5)).readyBatches())
                .containsExactly(List.of(new InboundConversationEvent("bia-1", "poc:bia", ReceptionMessageType.TEXT,
                        "Bia", null, null, null, START.plusSeconds(1), null)));
    }

    @Test
    void deduplicatesRepeatedEventIdsBeforeReleasingOneBatch() {
        MessageBatcher batcher = new MessageBatcher();
        InboundConversationEvent event = text("same-event", "Oi", START);

        batcher.accept(event);
        batcher.accept(event);

        assertThat(batcher.flushDue("poc:ana", START.plusSeconds(4)).readyBatches())
                .containsExactly(List.of(event));
    }

    @Test
    void forceFlushReleasesPendingTextBeforeItsMovingWindowIsDue() {
        MessageBatcher batcher = new MessageBatcher();
        InboundConversationEvent event = text("force-event", "Oi", START);

        batcher.accept(event);

        assertThat(batcher.forceFlush("poc:ana").readyBatches())
                .containsExactly(List.of(event));
        assertThat(batcher.pendingContacts()).isZero();
    }

    private static InboundConversationEvent text(String eventId, String text, Instant at) {
        return new InboundConversationEvent(eventId, "poc:ana", ReceptionMessageType.TEXT,
                text, null, null, null, at, null);
    }
}
