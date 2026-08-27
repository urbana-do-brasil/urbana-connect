package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.HumanHandoffNotification;

/** Durable idempotent notification boundary for human ownership. */
public interface HumanHandoffNotificationGateway {
    /** Returns false when this handoff was already notified. */
    boolean notifyIfAbsent(HumanHandoffNotification notification);
}
