package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.reception.model.DeliveryDispatchPhase;
import br.com.urbana.connect.domain.reception.model.DeliveryFailureClassifier;
import br.com.urbana.connect.domain.reception.port.out.DeliveryDestinationRegistryGateway;
import br.com.urbana.connect.interfaces.rest.WebhookCanonicalEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Hermes-first adapter for the official WhatsApp webhook. */
public final class HermesWebhookMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HermesWebhookMessageHandler.class);
    public static final String SAFE_HUMAN_HANDOFF_MESSAGE = "Recebemos sua mensagem. Vamos encaminhar seu atendimento para nossa equipe, que entrará em contato para ajudar.";

    private final ReceptionOrchestrator orchestrator;
    private final WhatsAppMessageGateway whatsapp;
    private final HermesFailureHandoffService failureHandoff;
    private final PocReceptionWorker worker;
    private final DeliveryDestinationRegistryGateway destinationRegistry;

    public HermesWebhookMessageHandler(ReceptionOrchestrator orchestrator,
                                       WhatsAppMessageGateway whatsapp) {
        this(orchestrator, whatsapp, null);
    }

    /**
     * Official wiring must provide {@code failureHandoff}; the two-argument
     * constructor only preserves compatibility for isolated legacy tests.
     */
    public HermesWebhookMessageHandler(ReceptionOrchestrator orchestrator,
                                       WhatsAppMessageGateway whatsapp,
                                       HermesFailureHandoffService failureHandoff) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp");
        this.failureHandoff = failureHandoff;
        this.worker = null;
        this.destinationRegistry = null;
    }

    /** Official webhook path: durable queue first, never a direct channel call. */
    public HermesWebhookMessageHandler(PocReceptionWorker worker) {
        this(worker, null);
    }

    /** Official wiring also records the provider address before queueing. */
    public HermesWebhookMessageHandler(PocReceptionWorker worker,
                                       DeliveryDestinationRegistryGateway destinationRegistry) {
        this.orchestrator = null;
        this.whatsapp = null;
        this.failureHandoff = null;
        this.worker = Objects.requireNonNull(worker, "worker");
        this.destinationRegistry = destinationRegistry;
    }

    public void handle(InboundWhatsAppMessage message, Instant receivedAt) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(receivedAt, "receivedAt");

        String rawType = message.messageType();
        if (rawType != null && !rawType.isBlank()
                && !Set.of("text", "interactive", "button_reply", "list_reply",
                "audio", "voice", "image", "photo", "document", "file", "payment_proof")
                .contains(rawType.toLowerCase(Locale.ROOT))) {
            // Unknown channel types are outside this story and must not be
            // guessed into a conversational transition.
            LOGGER.info("Mensagem WhatsApp ignorada pelo fluxo Hermes: type={} providerMessageId={}",
                    rawType, message.providerMessageId());
            return;
        }

        InboundConversationEvent event = WebhookCanonicalEventMapper.fromWhatsApp(message, receivedAt);

        if (worker != null) {
            if (destinationRegistry != null) {
                // The canonical contact id is intentionally one-way. The
                // protected registry preserves the channel address at the
                // boundary so an outbox callback can resolve it later.
                destinationRegistry.saveIfAbsent(event.contactId(), message.phoneNumber());
            }
            worker.enqueue(List.of(event));
            return;
        }

        ReceptionOrchestrator.TurnReceipt receipt;
        try {
            receipt = orchestrator.process(event);
        } catch (RuntimeException exception) {
            handoffAndAcknowledge(message, event, event.eventId(), "HERMES_PROCESSING_EXCEPTION", exception);
            return;
        }
        if (receipt.status() != ReceptionOrchestrator.TurnStatus.COMPLETED || receipt.output() == null) {
            LOGGER.info("Resposta WhatsApp não publicada: status={} eventId={} error={}",
                    receipt.status(), receipt.eventId(), receipt.error());
            if (receipt.status() != ReceptionOrchestrator.TurnStatus.DUPLICATE) {
                handoffAndAcknowledge(message, event, receipt.correlationId(),
                        "HERMES_" + receipt.status(), null);
            }
            return;
        }

        whatsapp.sendTextMessage(message.phoneNumber(), receipt.output().message());
    }

    private void handoffAndAcknowledge(InboundWhatsAppMessage message, InboundConversationEvent event,
                                       String correlationId, String reason, RuntimeException cause) {
        if (failureHandoff == null) {
            LOGGER.error("Falha Hermes sem persistência de handoff configurada: eventId={} reason={}",
                    event.eventId(), reason, cause);
            return;
        } else {
            try {
                failureHandoff.record(event, correlationId, reason);
            } catch (RuntimeException persistenceFailure) {
                // Do not falsely claim that a human was notified when durable persistence failed.
                LOGGER.error("Não foi possível registrar handoff humano para eventId={} reason={}",
                        event.eventId(), reason, persistenceFailure);
                return;
            }
        }

        try {
            whatsapp.sendTextMessage(message.phoneNumber(), SAFE_HUMAN_HANDOFF_MESSAGE);
        } catch (RuntimeException deliveryFailure) {
            LOGGER.warn("Resposta segura não confirmada: eventId={} disposition={} reason={}",
                    event.eventId(), DeliveryFailureClassifier.classify(DeliveryDispatchPhase.POST_DISPATCH_UNKNOWN),
                    reason, deliveryFailure);
        }
    }
}
