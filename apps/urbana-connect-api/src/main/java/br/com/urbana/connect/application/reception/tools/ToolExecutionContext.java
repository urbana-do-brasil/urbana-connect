package br.com.urbana.connect.application.reception.tools;

import br.com.urbana.connect.domain.reception.model.ActiveTurnLease;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Backend-resolved provenance for a domain tool invocation. */
public record ToolExecutionContext(ActiveTurnLease lease, Instant now) {
    public ToolExecutionContext {
        lease = Objects.requireNonNull(lease, "lease");
        now = Objects.requireNonNull(now, "now");
    }

    public String sourceMessageId() {
        return lease.sourceMessageId();
    }

    /** All inbound event ids released in this turn, in chronological order. */
    public List<String> sourceMessageIds() {
        return lease.sourceMessageIds();
    }
}
