package br.com.urbana.connect.application.conversation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class InboundWhatsAppRouterService {

    private final ConversationFlowService conversationFlowService;
    private final OpenClawPocService openClawPocService;
    private final boolean openClawPocEnabled;

    public InboundWhatsAppRouterService(
            ConversationFlowService conversationFlowService,
            OpenClawPocService openClawPocService,
            @Value("${openclaw.poc.enabled:false}") boolean openClawPocEnabled) {
        this.conversationFlowService = conversationFlowService;
        this.openClawPocService = openClawPocService;
        this.openClawPocEnabled = openClawPocEnabled;
    }

    public void handleIncomingMessage(InboundWhatsAppMessage inboundMessage, Instant receivedAt) {
        if (!openClawPocEnabled || !isEligibleForOpenClawPoc(inboundMessage)) {
            conversationFlowService.handleIncomingMessage(inboundMessage, receivedAt);
            return;
        }

        openClawPocService.handleTextTurn(inboundMessage, receivedAt);
    }

    private boolean isEligibleForOpenClawPoc(InboundWhatsAppMessage inboundMessage) {
        return "text".equalsIgnoreCase(inboundMessage.messageType())
            && inboundMessage.textBody() != null
            && !inboundMessage.textBody().isBlank()
            && !isGroupMessage(inboundMessage.phoneNumber());
    }

    private boolean isGroupMessage(String phoneNumber) {
        return phoneNumber != null && phoneNumber.endsWith("@g.us");
    }
}
