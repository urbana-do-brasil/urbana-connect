package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class GreetingFlowService {

    private static final Logger log = LoggerFactory.getLogger(GreetingFlowService.class);

    private final ConversationLifecycleService conversationLifecycleService;
    private final WhatsAppMessageGateway whatsAppMessageGateway;

    public GreetingFlowService(
            ConversationLifecycleService conversationLifecycleService,
            WhatsAppMessageGateway whatsAppMessageGateway) {
        this.conversationLifecycleService = conversationLifecycleService;
        this.whatsAppMessageGateway = whatsAppMessageGateway;
    }

    public Conversation handleIncomingMessage(String phoneNumber, Instant receivedAt) {
        Conversation conversation = conversationLifecycleService.resumeOrStart(phoneNumber, receivedAt);

        if (conversation.currentStep() == ConversationStep.GREETING) {
            try {
                whatsAppMessageGateway.sendGreeting(phoneNumber);
            } catch (RuntimeException exception) {
                log.error(
                    "Falha ao enviar saudacao para {} na etapa {}: {}",
                    phoneNumber,
                    conversation.currentStep(),
                    exception.getMessage()
                );
            }
        }

        return conversation;
    }
}
