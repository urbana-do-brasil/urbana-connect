package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.application.conversation.InboundWhatsAppMessage;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.reception.model.ReceptionMessageType;
import br.com.urbana.connect.interfaces.rest.WebhookCanonicalEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/** Hermes-first adapter for the official WhatsApp webhook. */
public final class HermesWebhookMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HermesWebhookMessageHandler.class);

    private final ReceptionOrchestrator orchestrator;
    private final WhatsAppMessageGateway whatsapp;

    public HermesWebhookMessageHandler(ReceptionOrchestrator orchestrator,
                                       WhatsAppMessageGateway whatsapp) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp");
    }

    public void handle(InboundWhatsAppMessage message, Instant receivedAt) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(receivedAt, "receivedAt");

        InboundConversationEvent event = WebhookCanonicalEventMapper.fromWhatsApp(message, receivedAt);
        if (event.type() != ReceptionMessageType.TEXT
                && event.type() != ReceptionMessageType.INTERACTIVE) {
            LOGGER.info("Mensagem WhatsApp ignorada pelo fluxo textual Hermes: type={} providerMessageId={}",
                    event.type(), event.providerMessageId());
            return;
        }

        ReceptionOrchestrator.TurnReceipt receipt = orchestrator.process(event);
        if (receipt.status() != ReceptionOrchestrator.TurnStatus.COMPLETED || receipt.output() == null) {
            LOGGER.info("Resposta WhatsApp não publicada: status={} eventId={} error={}",
                    receipt.status(), receipt.eventId(), receipt.error());
            return;
        }

        whatsapp.sendTextMessage(message.phoneNumber(), receipt.output().message());
    }
}
