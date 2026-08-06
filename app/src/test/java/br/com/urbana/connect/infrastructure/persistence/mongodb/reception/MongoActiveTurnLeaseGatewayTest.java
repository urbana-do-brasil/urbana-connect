package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLeaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MongoActiveTurnLeaseGatewayTest {
    private static final Instant ACQUIRED = Instant.parse("2026-08-05T12:00:00Z");
    private static final Instant NOW = ACQUIRED.plusSeconds(10);

    @Test
    void acquireReturnsEmptyWhenConditionalUpsertLosesRaceAndNeverReadsOrSaves() {
        SpringDataActiveTurnLeaseRepository repository = mock(SpringDataActiveTurnLeaseRepository.class);
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ActiveTurnLeaseDocument.class))).thenThrow(new DuplicateKeyException("same session"));

        assertThat(new MongoActiveTurnLeaseGateway(repository, template).acquire(lease("RUNNING"))).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void findRunningUsesConditionalReadAndReturnsOnlyStillRunningLease() {
        SpringDataActiveTurnLeaseRepository repository = mock(SpringDataActiveTurnLeaseRepository.class);
        MongoTemplate template = mock(MongoTemplate.class);
        ActiveTurnLeaseDocument document = document(lease("RUNNING"));
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ActiveTurnLeaseDocument.class))).thenReturn(document);

        Optional<ActiveTurnLease> result =
                new MongoActiveTurnLeaseGateway(repository, template).findRunning("hermes-1", NOW);

        assertThat(result).contains(lease("RUNNING"));
        verifyNoInteractions(repository);
    }

    @Test
    void findRunningMarksAnExpiredLeaseWithAConditionalTransitionAndReturnsEmpty() {
        SpringDataActiveTurnLeaseRepository repository = mock(SpringDataActiveTurnLeaseRepository.class);
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ActiveTurnLeaseDocument.class))).thenReturn(null);

        assertThat(new MongoActiveTurnLeaseGateway(repository, template)
                .findRunning("hermes-1", ACQUIRED.plusSeconds(61))).isEmpty();

        verify(template, org.mockito.Mockito.times(2)).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ActiveTurnLeaseDocument.class));
        verifyNoInteractions(repository);
    }

    @Test
    void revokeRejectsStaleTurnWithoutRepositoryReadOrSave() {
        SpringDataActiveTurnLeaseRepository repository = mock(SpringDataActiveTurnLeaseRepository.class);
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ActiveTurnLeaseDocument.class))).thenReturn(null);

        assertThatThrownBy(() -> new MongoActiveTurnLeaseGateway(repository, template)
                .revoke("hermes-1", "turn-stale", NOW))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void expireRejectsAConcurrentStateChangeWithoutRepositoryReadOrSave() {
        SpringDataActiveTurnLeaseRepository repository = mock(SpringDataActiveTurnLeaseRepository.class);
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ActiveTurnLeaseDocument.class))).thenReturn(null);

        assertThatThrownBy(() -> new MongoActiveTurnLeaseGateway(repository, template)
                .expire("hermes-1", "turn-1", ACQUIRED.plusSeconds(61)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    private static ActiveTurnLease lease(String status) {
        return new ActiveTurnLease("hermes-1", "turn-1", "contact-1", "message-1",
                ActiveTurnLeaseStatus.valueOf(status), ACQUIRED, ACQUIRED.plusSeconds(60),
                null, 0);
    }

    private static ActiveTurnLeaseDocument document(ActiveTurnLease lease) {
        ActiveTurnLeaseDocument document = new ActiveTurnLeaseDocument();
        document.setHermesSessionId(lease.hermesSessionId());
        document.setTurnId(lease.turnId());
        document.setContactId(lease.contactId());
        document.setSourceMessageId(lease.sourceMessageId());
        document.setStatus(lease.status());
        document.setAcquiredAt(lease.acquiredAt());
        document.setExpiresAt(lease.expiresAt());
        document.setRevokedAt(lease.revokedAt());
        document.setVersion(lease.version());
        return document;
    }
}
