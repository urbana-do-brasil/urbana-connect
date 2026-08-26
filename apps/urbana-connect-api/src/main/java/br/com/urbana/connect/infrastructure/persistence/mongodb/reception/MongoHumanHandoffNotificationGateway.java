package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.HumanHandoffNotification;
import br.com.urbana.connect.domain.reception.port.out.HumanHandoffNotificationGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** Durable internal outbox record for a handoff notification. */
@Component
@ConditionalOnProperty(name = "hermes.poc.enabled", havingValue = "true")
public final class MongoHumanHandoffNotificationGateway implements HumanHandoffNotificationGateway {
    private final SpringDataHumanHandoffNotificationRepository repository;

    public MongoHumanHandoffNotificationGateway(SpringDataHumanHandoffNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean notifyIfAbsent(HumanHandoffNotification notification) {
        if (repository.findById(notification.notificationId()).isPresent()) {
            return false;
        }
        try {
            repository.save(toDocument(notification));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    private static HumanHandoffNotificationDocument toDocument(HumanHandoffNotification notification) {
        HumanHandoffNotificationDocument document = new HumanHandoffNotificationDocument();
        document.setNotificationId(notification.notificationId());
        document.setIdempotencyKey(notification.idempotencyKey());
        document.setConversationId(notification.conversationId());
        document.setTurnId(notification.turnId());
        document.setReason(notification.reason());
        document.setServiceType(notification.serviceType());
        document.setCommercialStage(notification.commercialStage());
        document.setPaymentStatus(notification.paymentStatus());
        document.setPresentIcpFields(notification.presentIcpFields());
        document.setMissingIcpFields(notification.missingIcpFields());
        document.setOccurredAt(notification.occurredAt());
        return document;
    }
}
