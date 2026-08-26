package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.IcpObservationEvent;
import br.com.urbana.connect.domain.reception.port.out.IcpObservationEventGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** Mongo-backed sink for internal ICP observations, separate from the transcript. */
@Component
@ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
public final class MongoIcpObservationEventGateway implements IcpObservationEventGateway {
    private final SpringDataIcpObservationEventRepository repository;

    public MongoIcpObservationEventGateway(SpringDataIcpObservationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean appendIfAbsent(IcpObservationEvent event) {
        if (repository.findById(event.eventId()).isPresent()) {
            return false;
        }
        try {
            repository.save(toDocument(event));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    private static IcpObservationEventDocument toDocument(IcpObservationEvent event) {
        IcpObservationEventDocument document = new IcpObservationEventDocument();
        document.setEventId(event.eventId());
        document.setEventType(event.eventType());
        document.setConversationId(event.conversationId());
        document.setTurnId(event.turnId());
        document.setServiceType(event.serviceType());
        document.setMissingFields(event.missingFields());
        document.setDetectionPoint(event.detectionPoint());
        document.setIdempotencyKey(event.idempotencyKey());
        document.setOccurredAt(event.occurredAt());
        return document;
    }
}
