package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway;

import java.time.Clock;
import java.time.Instant;

/** Resolves the persistent Hermes session without rebuilding normal history. */
public class HermesSessionService {
    private final HermesSessionsGateway sessions;
    private final AgentSessionLinkGateway links;
    private final Clock clock;

    public HermesSessionService(HermesSessionsGateway sessions, AgentSessionLinkGateway links) {
        this(sessions, links, Clock.systemUTC());
    }

    public HermesSessionService(HermesSessionsGateway sessions, AgentSessionLinkGateway links, Clock clock) {
        this.sessions = sessions;
        this.links = links;
        this.clock = clock;
    }

    public SessionResolution resolve(String contactId) {
        Instant now = clock.instant();
        return links.findActiveByContactId(contactId)
                .map(link -> {
                    AgentSessionLink touched = links.touchActive(contactId, link.hermesSessionId(), now);
                    return new SessionResolution(touched.hermesSessionId(), false, false, touched);
                })
                .orElseGet(() -> create(contactId, now, false));
    }

    public SessionResolution resolveOrCreate(String contactId) {
        return resolve(contactId);
    }

    public HermesSessionsGateway.HermesChatResult chat(String contactId, HermesSessionsGateway.HermesChatRequest request) {
        SessionResolution resolution = resolve(contactId);
        try {
            HermesSessionsGateway.HermesChatResult result = sessions.chat(resolution.sessionId(), request);
            return reconcileRotation(contactId, resolution.link(), result);
        } catch (HttpHermesSessionsGateway.HermesSessionsException exception) {
            if (exception.status() != 404) {
                throw exception;
            }
            return recoverLostSession(contactId, resolution.link(), request);
        }
    }

    public HermesSessionsGateway.HermesHistorySnapshot historySnapshot(String sessionId) {
        return sessions.historySnapshot(sessionId);
    }

    public SessionResolution recover(String contactId, String lostSessionId) {
        Instant now = clock.instant();
        // Create in Hermes before the CAS. A failed CAS leaves only an
        // orphaned Hermes session for later cleanup; it never leaves Mongo
        // without an ACTIVE contact mapping.
        String replacementSessionId = sessions.createSession(contactId);
        AgentSessionLink replacement = AgentSessionLink.active(contactId, replacementSessionId, now);
        try {
            AgentSessionLink saved = links.replaceActive(contactId, lostSessionId, replacement,
                    br.com.urbana.connect.domain.reception.model.SessionLinkStatus.LOST);
            return new SessionResolution(saved.hermesSessionId(), true, true, saved);
        } catch (IllegalStateException race) {
            AgentSessionLink winner = links.findActiveByContactId(contactId)
                    .orElseThrow(() -> new IllegalStateException("session recovery lost CAS and no active winner exists", race));
            return new SessionResolution(winner.hermesSessionId(), false, true, winner);
        }
    }

    private HermesSessionsGateway.HermesChatResult recoverLostSession(String contactId, AgentSessionLink previous,
                                                                        HermesSessionsGateway.HermesChatRequest request) {
        SessionResolution replacement = recover(contactId, previous.hermesSessionId());
        HermesSessionsGateway.HermesChatResult result = sessions.chat(replacement.sessionId(), request);
        return reconcileRotation(contactId, replacement.link(), result);
    }

    private HermesSessionsGateway.HermesChatResult reconcileRotation(String contactId, AgentSessionLink current,
                                                                      HermesSessionsGateway.HermesChatResult result) {
        Instant now = clock.instant();
        if (!result.effectiveSessionId().equals(current.hermesSessionId())) {
            AgentSessionLink replacement = AgentSessionLink.active(contactId, result.effectiveSessionId(), now);
            links.replaceActive(contactId, current.hermesSessionId(), replacement,
                    br.com.urbana.connect.domain.reception.model.SessionLinkStatus.REPLACED);
            return result;
        }
        links.touchActive(contactId, current.hermesSessionId(), now);
        return result;
    }

    private SessionResolution create(String contactId, Instant now, boolean recovered) {
        String sessionId = sessions.createSession(contactId);
        AgentSessionLink link = AgentSessionLink.active(contactId, sessionId, now);
        AgentSessionLink saved = links.createIfAbsent(link);
        return new SessionResolution(saved.hermesSessionId(), saved.hermesSessionId().equals(sessionId), recovered, saved);
    }

    public record SessionResolution(String sessionId, boolean created, boolean recovered, AgentSessionLink link) {
        public SessionResolution {
            if (sessionId == null || sessionId.isBlank() || link == null) {
                throw new IllegalArgumentException("session resolution is incomplete");
            }
        }
    }
}
