package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.ReceptionMessage;

import java.util.List;
import java.util.Optional;

public interface ReceptionTranscriptGateway {
    /** Returns false when an inbound event already exists. */
    boolean appendIfAbsent(ReceptionMessage message);

    default ReceptionMessage saveIfAbsent(ReceptionMessage message) {
        appendIfAbsent(message);
        return message;
    }

    Optional<ReceptionMessage> findByEventId(String eventId);

    List<ReceptionMessage> findByConversationId(String conversationId);
}
