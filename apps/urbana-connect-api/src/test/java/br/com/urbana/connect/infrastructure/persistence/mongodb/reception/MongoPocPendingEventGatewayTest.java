package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.application.reception.InboundConversationEvent;
import br.com.urbana.connect.domain.reception.model.PocPendingEvent;
import br.com.urbana.connect.domain.reception.model.PocPendingEventStatus;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoPocPendingEventGatewayTest {
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");

    @Test
    void atomicallyRequeuesOnlyTheCurrentClaimForAConfirmedSafeRetry() {
        SpringDataPocPendingEventRepository repository = mock(SpringDataPocPendingEventRepository.class);
        PocPendingEvent event = PocPendingEvent.claimed(
                event("retry-1", "poc:ana", NOW.minusSeconds(1)), "claim-1", NOW.minusSeconds(1));
        PocPendingEventDocument document = document(event);
        when(repository.findById("retry-1")).thenReturn(Optional.of(document));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MongoPocPendingEventGateway gateway = new MongoPocPendingEventGateway(repository, null);

        Optional<PocPendingEvent> requeued = gateway.requeueIfRetrySafe("retry-1", "claim-1", NOW);

        assertThat(requeued).get().extracting(PocPendingEvent::status)
                .isEqualTo(PocPendingEventStatus.QUEUED);
        assertThat(requeued.orElseThrow().claimToken()).isNull();
        verify(repository, times(1)).save(any(PocPendingEventDocument.class));

        when(repository.findById("retry-1")).thenReturn(Optional.of(document(event)));
        assertThat(gateway.requeueIfRetrySafe("retry-1", "wrong-claim", NOW)).isEmpty();
        verify(repository, times(1)).save(any(PocPendingEventDocument.class));
    }

    @Test
    void recoversEventsInContactOccurredAndAcceptedOrder() {
        SpringDataPocPendingEventRepository repository = mock(SpringDataPocPendingEventRepository.class);
        when(repository.findByStatusInOrderByContactIdAscOccurredAtAscAcceptedAtAscEventIdAsc(anyList()))
                .thenReturn(List.of(
                        document(PocPendingEvent.accepted(event("bia-1", "poc:bia", NOW.minusSeconds(1)),
                                NOW.minusSeconds(1))),
                        document(PocPendingEvent.accepted(event("ana-2", "poc:ana", NOW.minusSeconds(3)),
                                NOW.minusSeconds(2))),
                        document(PocPendingEvent.accepted(event("ana-1", "poc:ana", NOW.minusSeconds(5)),
                                NOW.minusSeconds(4)))));
        MongoPocPendingEventGateway gateway = new MongoPocPendingEventGateway(repository, null);

        assertThat(gateway.findRecoverable(NOW, Duration.ofSeconds(30)))
                .extracting(PocPendingEvent::eventId)
                .containsExactly("ana-1", "ana-2", "bia-1");
    }

    private static InboundConversationEvent event(String id, String contact, Instant occurredAt) {
        return new InboundConversationEvent(id, contact, ReceptionMessageType.TEXT, id, occurredAt);
    }

    private static PocPendingEventDocument document(PocPendingEvent event) {
        PocPendingEventDocument document = new PocPendingEventDocument();
        document.setEventId(event.eventId());
        document.setContactId(event.contactId());
        document.setType(event.type());
        document.setText(event.text());
        document.setOccurredAt(event.occurredAt());
        document.setAcceptedAt(event.acceptedAt());
        document.setStatus(event.status());
        document.setClaimToken(event.claimToken());
        document.setClaimedAt(event.claimedAt());
        document.setCompletedAt(event.completedAt());
        return document;
    }
}
