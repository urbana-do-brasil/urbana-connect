package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.HumanHandoffNotification;
import br.com.urbana.connect.domain.reception.model.IcpObservationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoReceptionBoundaryGatewayTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void storesIcpObservationOutsideTheTranscriptAndReplaysTheSameEventAsNoop() {
        SpringDataIcpObservationEventRepository repository = mock(SpringDataIcpObservationEventRepository.class);
        IcpObservationEvent event = IcpObservationEvent.beforeTerms("conversation-1", "turn-1",
                "DECOR_PINTURA", List.of("OCCUPATION"), "icp-key-1", NOW);
        when(repository.findById(event.eventId())).thenReturn(Optional.empty(), Optional.of(document(event)));
        when(repository.save(any(IcpObservationEventDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MongoIcpObservationEventGateway gateway = new MongoIcpObservationEventGateway(repository);

        assertThat(gateway.appendIfAbsent(event)).isTrue();
        assertThat(gateway.appendIfAbsent(event)).isFalse();
        verify(repository, times(1)).save(any(IcpObservationEventDocument.class));
    }

    @Test
    void handoffNotificationTreatsAConcurrentDuplicateAsAnIdempotentNoop() {
        SpringDataHumanHandoffNotificationRepository repository =
                mock(SpringDataHumanHandoffNotificationRepository.class);
        HumanHandoffNotification notification = HumanHandoffNotification.create("handoff-key-1",
                "conversation-1", "turn-1", "cliente pediu uma pessoa", "DECOR_PINTURA",
                "ICP", "NOT_STARTED", List.of("OCCUPATION"), List.of("PRONOUN_PREFERENCE"), NOW);
        when(repository.findById(notification.notificationId())).thenReturn(Optional.empty());
        when(repository.save(any(HumanHandoffNotificationDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        MongoHumanHandoffNotificationGateway gateway = new MongoHumanHandoffNotificationGateway(repository);

        assertThat(gateway.notifyIfAbsent(notification)).isFalse();
        verify(repository).save(any(HumanHandoffNotificationDocument.class));
    }

    private static IcpObservationEventDocument document(IcpObservationEvent event) {
        IcpObservationEventDocument document = new IcpObservationEventDocument();
        document.setEventId(event.eventId());
        return document;
    }
}
