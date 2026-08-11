package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.model.SessionLinkStatus;

import java.time.Instant;
import java.util.Optional;

public interface AgentSessionLinkGateway {
    Optional<AgentSessionLink> findActiveByContactId(String contactId);

    Optional<AgentSessionLink> findBySessionId(String sessionId);

    /** Insert a first link without overwriting a concurrent winner. */
    AgentSessionLink createIfAbsent(AgentSessionLink link);

    /** Touch only the active session that the caller actually resolved. */
    AgentSessionLink touchActive(String contactId, String expectedSessionId, Instant lastUsedAt);

    /** Atomically replace the current active session for a contact. */
    AgentSessionLink replaceActive(String contactId, String expectedSessionId, AgentSessionLink replacement,
                                   SessionLinkStatus previousStatus);
}
