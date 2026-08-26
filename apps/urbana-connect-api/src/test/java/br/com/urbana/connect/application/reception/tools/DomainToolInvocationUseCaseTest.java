package br.com.urbana.connect.application.reception.tools;

import br.com.urbana.connect.application.reception.ActiveTurnLeaseService;
import br.com.urbana.connect.application.reception.ReceptionMetrics;
import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocation;
import br.com.urbana.connect.domain.reception.model.DomainToolInvocationStatus;
import br.com.urbana.connect.domain.reception.model.DomainToolName;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;
import br.com.urbana.connect.domain.reception.port.out.ReceptionConversationGateway;
import br.com.urbana.connect.domain.reception.port.out.ActiveTurnLeaseGateway;
import br.com.urbana.connect.domain.reception.port.out.DomainToolInvocationGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainToolInvocationUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void derivesStableKeyAndDoesNotExecuteTheSameToolTwice() {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = new ActiveTurnLeaseService(leaseGateway,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(1));
        leases.acquire("session-1", "turn-1", "contact-1", "message-1");
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        int[] executions = {0};
        DomainToolService tool = (name, contact, args) -> {
            executions[0]++;
            return Map.of("status", "OK");
        };
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var first = useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                Map.of("serviceType", "DECOR"));
        var duplicate = useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                Map.of("serviceType", "DECOR"));

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.idempotencyKey()).isEqualTo(first.idempotencyKey());
        assertThat(executions[0]).isEqualTo(1);
    }

    @Test
    void recordsOnlyNewDurableToolClaimsInReceptionMetrics() {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = activeLease(leaseGateway);
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        ReceptionMetrics metrics = new ReceptionMetrics();
        DomainToolService tool = (name, contact, args) -> Map.of("status", "OK");
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC), null, metrics);

        useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                Map.of("serviceType", "DECOR"));
        useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                Map.of("serviceType", "DECOR"));

        assertThat(metrics.snapshot().toolInvocations()).isEqualTo(1);
    }

    @Test
    void canonicalizesNestedMapOrderAndReplaysTheFullSuccessfulPayloadSnapshot() {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = activeLease(leaseGateway);
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        Map<String, Object> originalPayload = new LinkedHashMap<>();
        originalPayload.put("status", "PRESENTED");
        originalPayload.put("details", new LinkedHashMap<>(Map.of("z", 3, "a", List.of("one", "two"))));
        DomainToolService tool = (name, contact, args) -> originalPayload;
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC));

        Map<String, Object> firstNested = new LinkedHashMap<>();
        firstNested.put("z", 3);
        firstNested.put("a", List.of("one", "two"));
        Map<String, Object> firstArguments = new LinkedHashMap<>();
        firstArguments.put("metadata", firstNested);
        firstArguments.put("serviceType", "DECOR");

        Map<String, Object> reorderedNested = new LinkedHashMap<>();
        reorderedNested.put("a", List.of("one", "two"));
        reorderedNested.put("z", 3);
        Map<String, Object> reorderedArguments = new LinkedHashMap<>();
        reorderedArguments.put("serviceType", "DECOR");
        reorderedArguments.put("metadata", reorderedNested);

        var first = useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                firstArguments);
        // Mutating the object returned by the first execution must not alter the durable replay snapshot.
        originalPayload.put("status", "MUTATED_AFTER_SAVE");
        var duplicate = useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                reorderedArguments);

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.idempotencyKey()).isEqualTo(first.idempotencyKey());
        assertThat(duplicate.result()).isEqualTo(Map.of("status", "PRESENTED",
                "details", Map.of("z", 3, "a", List.of("one", "two"))));
        assertThat(duplicate.result()).isNotSameAs(first.result());
        assertThat(invocations.findByIdempotencyKey(first.idempotencyKey()).orElseThrow().resultPayload())
                .isEqualTo(duplicate.result());
    }

    @Test
    void snapshotsJavaTimeValuesReturnedByAProfileTool() {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = activeLease(leaseGateway);
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        CustomerFact fact = CustomerFact.confirmed("contact-1", "OCCUPATION", "DESIGNER",
                "message-1", NOW);
        DomainToolService tool = (name, contact, args) -> Map.of("facts", List.of(fact),
                "missingIcpFields", List.of("PRONOUN_PREFERENCE"));
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = useCase.invoke("session-1", "hermes-urbana-domain",
                DomainToolName.GET_CUSTOMER_PROFILE, Map.of());

        assertThat(result.result()).isInstanceOf(Map.class);
        assertThat(result.result().toString()).contains("DESIGNER", NOW.toString());
    }

    @Test
    void duplicateStartedInvocationReturnsInProgressConflictWithoutExecutingAgain() throws Exception {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = activeLease(leaseGateway);
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        CountDownLatch firstExecutionEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstExecution = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        DomainToolService tool = (name, contact, args) -> {
            executions.incrementAndGet();
            firstExecutionEntered.countDown();
            await(releaseFirstExecution);
            return Map.of("status", "OK");
        };
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<DomainToolInvocationUseCase.InvocationResult> first = executor.submit(() -> useCase.invoke(
                "session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                Map.of("serviceType", "DECOR")));
        assertThat(firstExecutionEntered.await(2, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                Map.of("serviceType", "DECOR")))
                .isInstanceOf(DomainToolInvocationUseCase.InvocationInProgressException.class);

        releaseFirstExecution.countDown();
        assertThat(first.get(2, TimeUnit.SECONDS).result()).isEqualTo(Map.of("status", "OK"));
        assertThat(executions).hasValue(1);
        executor.shutdownNow();
    }

    @Test
    void duplicateFailedInvocationReturnsDurableFailureAndNeverFalseSuccess() {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = activeLease(leaseGateway);
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        AtomicInteger executions = new AtomicInteger();
        DomainToolService tool = (name, contact, args) -> {
            executions.incrementAndGet();
            throw new IllegalStateException("fixture rejected");
        };
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                Map.of("serviceType", "DECOR")))
                .isInstanceOf(DomainToolInvocationUseCase.TechnicalToolFailureException.class)
                .hasMessage("Não consegui concluir esta etapa agora.");
        String key = invocations.lastKey();

        assertThatThrownBy(() -> useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                Map.of("serviceType", "DECOR")))
                .isInstanceOf(DomainToolInvocationUseCase.DurableToolFailureException.class)
                .satisfies(error -> {
                    var failure = (DomainToolInvocationUseCase.DurableToolFailureException) error;
                    assertThat(failure.resultCode()).isEqualTo("TECHNICAL_FAILURE");
                    assertThat(failure.resultPayload().toString())
                            .doesNotContain("fixture rejected", "IllegalStateException", "stack");
                });
        assertThat(executions).hasValue(1);
        assertThat(invocations.findByIdempotencyKey(key).orElseThrow().status())
                .isEqualTo(DomainToolInvocationStatus.FAILED);
    }

    @Test
    void persistsAndReplaysStructuredBusinessRejectionWithoutExecutingAgain() {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = activeLease(leaseGateway);
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        AtomicInteger executions = new AtomicInteger();
        DomainToolService tool = (name, contact, args) -> {
            executions.incrementAndGet();
            throw new DomainToolInvocationUseCase.DomainRejectionException(
                    "TERMS_NOT_ACCEPTED", "ASK_FOR_CLEAR_ACCEPTANCE", List.of(),
                    "Antes do pagamento, preciso do seu aceite claro dos termos.");
        };
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC));

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> useCase.invoke("session-1", "hermes-urbana-domain",
                    DomainToolName.PREPARE_PAYMENT, Map.of("serviceType", "DECOR", "method", "PIX")))
                    .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                    .satisfies(error -> assertThat(
                            ((DomainToolInvocationUseCase.DomainRejectionException) error).nextAction())
                            .isEqualTo("ASK_FOR_CLEAR_ACCEPTANCE"));
        }

        assertThat(executions).hasValue(1);
        assertThat(invocations.values.values()).singleElement()
                .extracting(DomainToolInvocation::status).isEqualTo(DomainToolInvocationStatus.REJECTED);
    }

    @Test
    void concurrentUniqueClaimAllowsOnlyOneDomainExecution() throws Exception {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = activeLease(leaseGateway);
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        CountDownLatch firstExecutionEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstExecution = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        DomainToolService tool = (name, contact, args) -> {
            executions.incrementAndGet();
            firstExecutionEntered.countDown();
            await(releaseFirstExecution);
            return Map.of("status", "OK");
        };
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<DomainToolInvocationUseCase.InvocationResult> first = executor.submit(() -> {
            return useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS,
                    Map.of("serviceType", "DECOR"));
        });
        assertThat(firstExecutionEntered.await(2, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> useCase.invoke("session-1", "hermes-urbana-domain",
                DomainToolName.PREPARE_TERMS, Map.of("serviceType", "DECOR")))
                .isInstanceOf(DomainToolInvocationUseCase.InvocationInProgressException.class);

        releaseFirstExecution.countDown();
        assertThat(first.get(2, TimeUnit.SECONDS).result()).isEqualTo(Map.of("status", "OK"));
        assertThat(executions).hasValue(1);
        executor.shutdownNow();
    }

    @Test
    void rejectsLateToolInvocationAfterHumanHandoffBeforeCreatingLedgerEntry() {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = activeLease(leaseGateway);
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        AtomicInteger executions = new AtomicInteger();
        DomainToolService tool = (name, contact, args) -> {
            executions.incrementAndGet();
            return Map.of("status", "MUTATED");
        };
        ReceptionConversation human = ReceptionConversation.start("contact-1", NOW)
                .requestHumanHandoff("cliente pediu uma pessoa", NOW);
        ReceptionConversationGateway conversations = new ReceptionConversationGateway() {
            @Override public Optional<ReceptionConversation> findByContactId(String contactId) {
                return Optional.of(human);
            }
            @Override public ReceptionConversation save(ReceptionConversation conversation) {
                return conversation;
            }
        };
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC), conversations);

        assertThatThrownBy(() -> useCase.invoke("session-1", "hermes-urbana-domain",
                DomainToolName.UPDATE_CUSTOMER_FACT,
                Map.of("factType", "OCCUPATION", "value", "DESIGNER")))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> {
                    DomainToolInvocationUseCase.DomainRejectionException rejection =
                            (DomainToolInvocationUseCase.DomainRejectionException) error;
                    assertThat(rejection.code()).isEqualTo("HUMAN_OWNS_CONVERSATION");
                    assertThat(rejection.customerMessage()).doesNotContain("HUMAN");
                });
        assertThat(executions).hasValue(0);
        assertThat(invocations.values).isEmpty();
    }

    @Test
    void rejectsReplayOfASuccessfulToolAfterConversationEntersHumanMode() {
        FakeLeaseGateway leaseGateway = new FakeLeaseGateway();
        ActiveTurnLeaseService leases = activeLease(leaseGateway);
        FakeInvocationGateway invocations = new FakeInvocationGateway();
        AtomicInteger executions = new AtomicInteger();
        DomainToolService tool = (name, contact, args) -> {
            executions.incrementAndGet();
            return Map.of("status", "OK");
        };
        ReceptionConversation ai = ReceptionConversation.start("contact-1", NOW);
        ReceptionConversation human = ai.requestHumanHandoff("cliente pediu uma pessoa", NOW);
        final ReceptionConversation[] current = {ai};
        ReceptionConversationGateway conversations = new ReceptionConversationGateway() {
            @Override public Optional<ReceptionConversation> findByContactId(String contactId) {
                return Optional.of(current[0]);
            }
            @Override public ReceptionConversation save(ReceptionConversation conversation) {
                current[0] = conversation;
                return conversation;
            }
        };
        DomainToolInvocationUseCase useCase = new DomainToolInvocationUseCase(leases, invocations, tool,
                Clock.fixed(NOW, ZoneOffset.UTC), conversations);
        Map<String, Object> arguments = Map.of("serviceType", "DECOR");
        useCase.invoke("session-1", "hermes-urbana-domain", DomainToolName.PREPARE_TERMS, arguments);
        current[0] = human;

        assertThatThrownBy(() -> useCase.invoke("session-1", "hermes-urbana-domain",
                DomainToolName.PREPARE_TERMS, arguments))
                .isInstanceOf(DomainToolInvocationUseCase.DomainRejectionException.class)
                .satisfies(error -> {
                    DomainToolInvocationUseCase.DomainRejectionException rejection =
                            (DomainToolInvocationUseCase.DomainRejectionException) error;
                    assertThat(rejection.code()).isEqualTo("HUMAN_OWNS_CONVERSATION");
                    assertThat(rejection.customerMessage()).doesNotContain("HUMAN");
                });
        assertThat(executions).hasValue(1);
    }

    private static ActiveTurnLeaseService activeLease(FakeLeaseGateway leaseGateway) {
        ActiveTurnLeaseService leases = new ActiveTurnLeaseService(leaseGateway,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(1));
        leases.acquire("session-1", "turn-1", "contact-1", "message-1");
        return leases;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("latch interrupted", exception);
        }
    }

    private static final class FakeInvocationGateway implements DomainToolInvocationGateway {
        private final Map<String, DomainToolInvocation> values = new HashMap<>();

        @Override
        public Optional<DomainToolInvocation> findByIdempotencyKey(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public synchronized DomainToolInvocation save(DomainToolInvocation invocation) {
            if (invocation.status() == DomainToolInvocationStatus.STARTED
                    && values.containsKey(invocation.idempotencyKey())) {
                throw new org.springframework.dao.DuplicateKeyException(invocation.idempotencyKey());
            }
            values.put(invocation.idempotencyKey(), invocation);
            return invocation;
        }

        String lastKey() {
            return values.keySet().stream().findFirst().orElseThrow();
        }
    }

    private static final class FakeLeaseGateway implements ActiveTurnLeaseGateway {
        private ActiveTurnLease lease;

        @Override
        public Optional<ActiveTurnLease> acquire(ActiveTurnLease requested) {
            if (lease != null && lease.isActiveAt(requested.acquiredAt())) {
                return Optional.empty();
            }
            lease = requested;
            return Optional.of(requested);
        }

        @Override
        public Optional<ActiveTurnLease> findRunning(String sessionId, Instant now) {
            return lease != null && lease.hermesSessionId().equals(sessionId) && lease.isActiveAt(now)
                    ? Optional.of(lease) : Optional.empty();
        }

        @Override
        public ActiveTurnLease revoke(String sessionId, String turnId, Instant now) {
            lease = lease.revoke(now);
            return lease;
        }

        @Override
        public ActiveTurnLease expire(String sessionId, String turnId, Instant now) {
            lease = lease.expire(now);
            return lease;
        }
    }
}
