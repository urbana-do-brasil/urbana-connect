package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.ReceptionMessage;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

public class MongoReceptionTranscriptGateway implements ReceptionTranscriptGateway {
    private final SpringDataReceptionMessageRepository repository;

    public MongoReceptionTranscriptGateway(SpringDataReceptionMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean appendIfAbsent(ReceptionMessage message) {
        if (repository.findByEventId(message.eventId()).isPresent()) {
            return false;
        }
        try {
            repository.save(toDocument(message));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<ReceptionMessage> findByEventId(String eventId) {
        return repository.findByEventId(eventId).map(this::toDomain);
    }

    @Override
    public List<ReceptionMessage> findByConversationId(String conversationId) {
        return repository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream().map(this::toDomain).toList();
    }

    private ReceptionMessageDocument toDocument(ReceptionMessage value) {
        ReceptionMessageDocument d = new ReceptionMessageDocument();
        d.setId(value.id()); d.setEventId(value.eventId()); d.setCorrelationId(value.correlationId());
        d.setConversationId(value.conversationId()); d.setContactId(value.contactId()); d.setDirection(value.direction());
        d.setSenderType(value.senderType()); d.setType(value.type()); d.setText(value.text()); d.setMediaRef(value.mediaRef());
        d.setProviderMessageId(value.providerMessageId()); d.setCreatedAt(value.createdAt());
        return d;
    }

    private ReceptionMessage toDomain(ReceptionMessageDocument d) {
        return new ReceptionMessage(d.getId(), d.getEventId(), d.getCorrelationId(), d.getConversationId(), d.getContactId(),
                d.getDirection(), d.getSenderType(), d.getType(), d.getText(), d.getMediaRef(), d.getProviderMessageId(), d.getCreatedAt());
    }
}
