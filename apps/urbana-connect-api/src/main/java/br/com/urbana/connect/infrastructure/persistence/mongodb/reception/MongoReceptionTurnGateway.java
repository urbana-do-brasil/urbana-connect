package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.AgentUsage;
import br.com.urbana.connect.domain.reception.model.ReceptionTurn;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;

import java.util.Optional;
import java.time.Instant;

public class MongoReceptionTurnGateway implements ReceptionTurnGateway {
    private final SpringDataReceptionTurnRepository repository;

    public MongoReceptionTurnGateway(SpringDataReceptionTurnRepository repository) {
        this.repository = repository;
    }

    @Override
    public ReceptionTurn save(ReceptionTurn turn) {
        return toDomain(repository.save(toDocument(turn)));
    }

    @Override
    public Optional<ReceptionTurn> findById(String turnId) {
        return repository.findById(turnId).map(this::toDomain);
    }

    @Override
    public Optional<ReceptionTurn> findByInboundMessageId(String messageId) {
        return repository.findByInboundMessageIdsContains(messageId).map(this::toDomain);
    }

    @Override
    public Optional<ReceptionTurn> findLatestByContactId(String contactId) {
        return repository.findFirstByContactIdOrderByAcceptedAtDesc(contactId).map(this::toDomain);
    }

    private ReceptionTurnDocument toDocument(ReceptionTurn t) {
        ReceptionTurnDocument d = new ReceptionTurnDocument();
        d.setId(t.id()); d.setCorrelationId(t.correlationId()); d.setContactId(t.contactId());
        d.setHermesSessionId(t.hermesSessionId()); d.setInboundMessageIds(t.inboundMessageIds()); d.setStatus(t.status());
        d.setAcceptedAt(t.acceptedAt());
        d.setStartedAt(t.startedAt()); d.setFinishedAt(t.finishedAt()); d.setFailureCode(t.failureCode());
        d.setAttempt(t.attempt()); d.setFailureClass(t.failureClass()); d.setRetryAllowed(t.retryAllowed());
        d.setHistoryCheckpoint(t.historyCheckpoint()); d.setVersion(t.version());
        AgentUsage usage = t.usage() == null ? AgentUsage.empty() : t.usage();
        d.setInputTokens(usage.inputTokens()); d.setOutputTokens(usage.outputTokens()); d.setTotalTokens(usage.totalTokens());
        if (t.output() != null) {
            d.setOutputMessage(t.output().message());
            d.setOutputNextAction(t.output().nextAction());
            d.setOutputHandoffReason(t.output().handoffReason());
        }
        return d;
    }

    private ReceptionTurn toDomain(ReceptionTurnDocument d) {
        br.com.urbana.connect.domain.reception.model.AgentOutput output = d.getOutputMessage() == null ? null
                : new br.com.urbana.connect.domain.reception.model.AgentOutput(d.getOutputMessage(),
                d.getOutputNextAction(), d.getOutputHandoffReason());
        Instant acceptedAt = d.getAcceptedAt() == null ? d.getStartedAt() : d.getAcceptedAt();
        int attempt = d.getAttempt() < 1 ? 1 : d.getAttempt();
        return new ReceptionTurn(d.getId(), d.getCorrelationId(), d.getContactId(), d.getHermesSessionId(),
                d.getInboundMessageIds(), d.getStatus(), acceptedAt, d.getStartedAt(), d.getFinishedAt(),
                attempt, d.getFailureClass(), d.isRetryAllowed(), d.getHistoryCheckpoint(), d.getVersion(),
                new AgentUsage(d.getInputTokens(), d.getOutputTokens(), d.getTotalTokens()), d.getFailureCode(), output);
    }
}
