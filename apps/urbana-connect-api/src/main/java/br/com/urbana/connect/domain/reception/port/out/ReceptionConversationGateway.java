package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.ReceptionConversation;

import java.util.Optional;

public interface ReceptionConversationGateway {
    Optional<ReceptionConversation> findByContactId(String contactId);

    ReceptionConversation save(ReceptionConversation conversation);

    /**
     * Persists a transition only when the caller still owns the version it
     * read. Implementations backed by a durable store should override this
     * with an atomic compare-and-set; small in-memory adapters retain the
     * ordinary save semantics for deterministic unit tests.
     */
    default ReceptionConversation saveExpected(ReceptionConversation conversation, long expectedVersion) {
        if (conversation.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("conversation version does not follow expected version");
        }
        return save(conversation);
    }
}
