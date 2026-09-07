package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.TermsConsentAuditEvent;
import br.com.urbana.connect.domain.reception.model.TermsConsentAuditEventType;
import br.com.urbana.connect.domain.reception.model.TermsConsentSession;
import br.com.urbana.connect.domain.reception.port.out.DeliveryOutboxGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentAuditEventGateway;
import br.com.urbana.connect.domain.reception.port.out.TermsConsentSessionGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TermsConsentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void emitsOpaqueSessionAndAcceptCreatesOnlyDurableOutboxContinuation() {
        MemorySessions sessions = new MemorySessions();
        MemoryEvents events = new MemoryEvents();
        MemoryOutbox outbox = new MemoryOutbox();
        TermsConsentService service = new TermsConsentService(sessions, events, outbox,
                null, Clock.fixed(NOW, ZoneOffset.UTC), "https://hml.example/", "secret");

        TermsConsentService.Issued result = service.issue(new TermsConsentService.IssueRequest(
                "conversation-1", "contact-1", "destination-1", "unit-1", "HML", "DECOR_INTERIORES",
                "terms-v1", "https://terms.example/v1", "Conteúdo aprovado", "turn-1"));

        assertThat(result.url()).startsWith("https://hml.example/termos#t=");
        assertThat(result.url()).doesNotContain("contact-1", "conversation-1");
        TermsConsentSession stored = sessions.value;
        assertThat(stored.tokenDigest()).doesNotContain(result.token());
        assertThat(events.values).extracting(TermsConsentAuditEvent::eventType)
                .containsExactly(TermsConsentAuditEventType.ISSUED);

        service.present(result.token());
        service.endReached(result.token());
        TermsConsentService.DecisionResult decision = service.decide(result.token(),
                TermsConsentService.Decision.ACCEPT);

        assertThat(decision.idempotent()).isFalse();
        assertThat(outbox.values).hasSize(2);
        assertThat(outbox.values).anySatisfy(item -> assertThat(item.eventKey()).isEqualTo(
                "terms:" + stored.presentationId() + ":payment-options"));
        assertThat(events.values).extracting(TermsConsentAuditEvent::eventType)
                .containsExactly(TermsConsentAuditEventType.ISSUED,
                        TermsConsentAuditEventType.PAGE_PRESENTED,
                        TermsConsentAuditEventType.END_REACHED,
                        TermsConsentAuditEventType.ACCEPTED);
    }

    private static final class MemorySessions implements TermsConsentSessionGateway {
        private TermsConsentSession value;

        @Override public TermsConsentSession saveIfAbsent(TermsConsentSession session) {
            if (value == null) value = session;
            return value;
        }
        @Override public Optional<TermsConsentSession> findByTokenDigest(String digest) {
            return Optional.ofNullable(value).filter(item -> item.tokenDigest().equals(digest));
        }
        @Override public Optional<TermsConsentSession> findByPresentationId(String id) {
            return Optional.ofNullable(value).filter(item -> item.presentationId().equals(id));
        }
        @Override public TermsConsentSession saveExpected(TermsConsentSession session, long expectedVersion) {
            value = session;
            return session;
        }
    }

    private static final class MemoryEvents implements TermsConsentAuditEventGateway {
        private final List<TermsConsentAuditEvent> values = new ArrayList<>();
        @Override public TermsConsentAuditEvent appendIfAbsent(TermsConsentAuditEvent event) {
            return values.stream().filter(item -> item.eventId().equals(event.eventId())).findFirst()
                    .orElseGet(() -> { values.add(event); return event; });
        }
        @Override public List<TermsConsentAuditEvent> findByPresentationId(String id) {
            return values.stream().filter(item -> item.presentationId().equals(id)).toList();
        }
    }

    private static final class MemoryOutbox implements DeliveryOutboxGateway {
        private final List<br.com.urbana.connect.domain.reception.model.DeliveryOutbox> values = new ArrayList<>();
        @Override public br.com.urbana.connect.domain.reception.model.DeliveryOutbox saveIfAbsent(
                br.com.urbana.connect.domain.reception.model.DeliveryOutbox item) {
            return values.stream().filter(value -> value.eventKey().equals(item.eventKey())).findFirst()
                    .orElseGet(() -> { values.add(item); return item; });
        }
        @Override public Optional<br.com.urbana.connect.domain.reception.model.DeliveryOutbox> findByEventKey(String eventKey) {
            return values.stream().filter(item -> item.eventKey().equals(eventKey)).findFirst();
        }
    }
}
