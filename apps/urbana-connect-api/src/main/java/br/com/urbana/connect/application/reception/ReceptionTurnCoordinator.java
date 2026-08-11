package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.port.out.ReceptionTranscriptGateway;
import br.com.urbana.connect.domain.reception.port.out.ReceptionTurnGateway;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes turns per contact and provides a process-local idempotency guard. */
public class ReceptionTurnCoordinator {
    private final Map<String, LockEntry> contactLocks = new ConcurrentHashMap<>();
    private final Map<String, Object> completedEvents = new ConcurrentHashMap<>();
    private final ReceptionTranscriptGateway transcript;
    private final ReceptionTurnGateway turns;

    public ReceptionTurnCoordinator() {
        this(null, null);
    }

    public ReceptionTurnCoordinator(ReceptionTranscriptGateway transcript, ReceptionTurnGateway turns) {
        this.transcript = transcript;
        this.turns = turns;
    }

    public <T> T serialize(String contactId, Supplier<T> operation) {
        String key = require(contactId);
        LockEntry entry = acquire(key);
        try {
            return operation.get();
        } finally {
            release(key, entry);
        }
    }

    public <T> ExecutionResult<T> execute(String contactId, String eventId, Supplier<T> operation) {
        require(eventId);
        return serialize(contactId, () -> {
            if (transcript != null) {
                Optional<?> existing = transcript.findByEventId(eventId);
                if (existing.isPresent()) {
                    @SuppressWarnings("unchecked") T prior = (T) completedEvents.get(eventId);
                    return new ExecutionResult<>(prior, true);
                }
            }
            if (completedEvents.containsKey(eventId)) {
                @SuppressWarnings("unchecked") T prior = (T) completedEvents.get(eventId);
                return new ExecutionResult<>(prior, true);
            }
            T value = operation.get();
            completedEvents.put(eventId, value);
            return new ExecutionResult<>(value, false);
        });
    }

    public <T> T executeOnce(String contactId, String eventId, Supplier<T> operation) {
        return execute(contactId, eventId, operation).value();
    }

    public <T> T callSerialized(String contactId, Callable<T> operation) throws Exception {
        String key = require(contactId);
        LockEntry entry = acquire(key);
        try {
            return operation.call();
        } finally {
            release(key, entry);
        }
    }

    public record ExecutionResult<T>(T value, boolean duplicate) { }

    private LockEntry acquire(String contactId) {
        LockEntry entry = contactLocks.compute(contactId, (ignored, current) -> {
            LockEntry retained = current == null ? new LockEntry() : current;
            retained.references++;
            return retained;
        });
        entry.lock.lock();
        return entry;
    }

    private void release(String contactId, LockEntry entry) {
        entry.lock.unlock();
        contactLocks.computeIfPresent(contactId, (ignored, current) -> {
            if (current != entry) {
                return current;
            }
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }

    private static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value;
    }
}
