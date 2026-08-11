package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentSessionLink;
import br.com.urbana.connect.domain.reception.port.out.AgentSessionLinkGateway;
import br.com.urbana.connect.domain.reception.port.out.HermesSessionsGateway;
import br.com.urbana.connect.infrastructure.hermes.HttpHermesSessionsGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HermesSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void createsOnceAndReusesThePersistentContactSession() {
        FakeSessions sessions = new FakeSessions();
        FakeLinks links = new FakeLinks();
        HermesSessionService service = new HermesSessionService(sessions, links, fixedClock());

        HermesSessionService.SessionResolution first = service.resolve("contact-1");
        HermesSessionService.SessionResolution second = service.resolve("contact-1");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(sessions.createdContacts).containsExactly("contact-1");
        assertThat(links.active("contact-1").version()).isEqualTo(1);
    }

    @Test
    void persistsEffectiveSessionWhenHermesRotatesTheRuntimeSession() {
        FakeSessions sessions = new FakeSessions();
        FakeLinks links = new FakeLinks();
        HermesSessionService service = new HermesSessionService(sessions, links, fixedClock());
        service.resolve("contact-1");
        sessions.nextResult = result("s-1", "s-2", "resposta");

        HermesSessionsGateway.HermesChatResult result = service.chat("contact-1",
                new HermesSessionsGateway.HermesChatRequest("oi"));

        assertThat(result.effectiveSessionId()).isEqualTo("s-2");
        assertThat(links.active("contact-1").hermesSessionId()).isEqualTo("s-2");
        assertThat(links.history.get("s-1").replacedBySessionId()).isEqualTo("s-2");
    }

    @Test
    void recoversOnlyWhenThePersistedSessionIsActuallyMissing() {
        FakeSessions sessions = new FakeSessions();
        FakeLinks links = new FakeLinks();
        HermesSessionService service = new HermesSessionService(sessions, links, fixedClock());
        service.resolve("contact-1");
        sessions.throwNotFoundOnce = true;
        sessions.nextResult = result("s-2", "s-2", "recuperada");

        HermesSessionsGateway.HermesChatResult result = service.chat("contact-1",
                new HermesSessionsGateway.HermesChatRequest("retomar"));

        assertThat(result.content()).isEqualTo("recuperada");
        assertThat(links.history.get("s-1").status())
                .isEqualTo(br.com.urbana.connect.domain.reception.model.SessionLinkStatus.LOST);
        assertThat(links.active("contact-1").hermesSessionId()).isEqualTo("s-2");
        assertThat(sessions.createdContacts).containsExactly("contact-1", "contact-1");
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static HermesSessionsGateway.HermesChatResult result(String requested, String effective, String content) {
        return new HermesSessionsGateway.HermesChatResult(requested, effective, content, null, Map.of());
    }

    private static final class FakeSessions implements HermesSessionsGateway {
        private int sequence;
        private final List<String> createdContacts = new ArrayList<>();
        private HermesChatResult nextResult;
        private boolean throwNotFoundOnce;

        @Override
        public String createSession(String contactId) {
            createdContacts.add(contactId);
            sequence++;
            return sequence == 1 ? "s-1" : "s-2";
        }

        @Override
        public HermesChatResult chat(String sessionId, HermesChatRequest request) {
            if (throwNotFoundOnce) {
                throwNotFoundOnce = false;
                throw HttpHermesSessionsGateway.HermesSessionsException.fromStatus(404, "missing");
            }
            return nextResult == null ? result(sessionId, sessionId, "ok") : nextResult;
        }

        @Override
        public List<HermesHistoryMessage> history(String sessionId) {
            return List.of();
        }
    }

    private static final class FakeLinks implements AgentSessionLinkGateway {
        private final Map<String, AgentSessionLink> active = new HashMap<>();
        private final Map<String, AgentSessionLink> history = new HashMap<>();

        AgentSessionLink active(String contactId) {
            return active.get(contactId);
        }

        @Override
        public Optional<AgentSessionLink> findActiveByContactId(String contactId) {
            return Optional.ofNullable(active.get(contactId));
        }

        @Override
        public Optional<AgentSessionLink> findBySessionId(String sessionId) {
            return history.values().stream().filter(link -> link.hermesSessionId().equals(sessionId)).findFirst();
        }

        @Override
        public AgentSessionLink createIfAbsent(AgentSessionLink link) {
            AgentSessionLink winner = active.putIfAbsent(link.contactId(), link);
            AgentSessionLink saved = winner == null ? link : winner;
            history.put(saved.hermesSessionId(), saved);
            return saved;
        }

        @Override
        public AgentSessionLink touchActive(String contactId, String expectedSessionId, Instant lastUsedAt) {
            AgentSessionLink current = active.get(contactId);
            if (current == null || !current.hermesSessionId().equals(expectedSessionId)) {
                throw new IllegalStateException("concurrent touch");
            }
            AgentSessionLink touched = current.touch(lastUsedAt);
            active.put(contactId, touched);
            history.put(touched.hermesSessionId(), touched);
            return touched;
        }

        private AgentSessionLink saveReplacement(AgentSessionLink link) {
            if (link.status() == br.com.urbana.connect.domain.reception.model.SessionLinkStatus.ACTIVE) {
                active.put(link.contactId(), link);
            }
            history.put(link.hermesSessionId(), link);
            return link;
        }

        @Override
        public AgentSessionLink replaceActive(String contactId, String expectedSessionId,
                                              AgentSessionLink replacement,
                                              br.com.urbana.connect.domain.reception.model.SessionLinkStatus previousStatus) {
            AgentSessionLink current = active.get(contactId);
            if (current == null || !current.hermesSessionId().equals(expectedSessionId)) {
                throw new IllegalStateException("concurrent replacement");
            }
            AgentSessionLink replaced = new AgentSessionLink(current.contactId(), current.hermesSessionId(),
                    previousStatus, current.createdAt(), replacement.lastUsedAt(), replacement.hermesSessionId(),
                    current.version() + 1);
            history.put(replaced.hermesSessionId(), replaced);
            return saveReplacement(replacement);
        }
    }
}
