package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import br.com.urbana.connect.domain.conversation.model.StepFallbackBehavior;
import br.com.urbana.connect.domain.conversation.model.StructuredEscapeType;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationActionExecutor {

    private final WhatsAppMessageGateway whatsAppMessageGateway;

    public ConversationActionExecutor(WhatsAppMessageGateway whatsAppMessageGateway) {
        this.whatsAppMessageGateway = whatsAppMessageGateway;
    }

    public void sendReply(String phoneNumber, String replyText) {
        whatsAppMessageGateway.sendTextMessage(phoneNumber, replyText);
    }

    public void executeFallback(
            String phoneNumber,
            Conversation conversation,
            StepContract stepContract,
            List<ServiceCatalogItem> availableServices,
            String defaultIcpPrompt,
            String defaultServiceDiscoveryPrompt) {
        StepFallbackBehavior behavior = stepContract.fallbackBehavior();
        whatsAppMessageGateway.sendUnknownInputFallback(phoneNumber);
        switch (behavior) {
            case REPEAT_GREETING_WITH_BUTTONS -> whatsAppMessageGateway.sendGreeting(phoneNumber);
            case REPEAT_ICP_WITH_REFRAME -> whatsAppMessageGateway.sendTextMessage(phoneNumber, defaultIcpPrompt);
            case REPEAT_SERVICE_DISCOVERY_WITH_OPTIONS -> {
                whatsAppMessageGateway.sendTextMessage(phoneNumber, defaultServiceDiscoveryPrompt);
                sendStructuredEscape(phoneNumber, conversation, stepContract.structuredEscapeType(), availableServices);
            }
            case REPEAT_CONFIRMATION -> availableServices.stream()
                .filter(service -> service.type() == conversation.selectedService())
                .findFirst()
                .ifPresent(service -> whatsAppMessageGateway.sendServicePresentation(phoneNumber, service));
            case REPEAT_TERMS -> whatsAppMessageGateway.sendTermsOfUse(phoneNumber);
            case REPEAT_PAYMENT_OPTIONS -> whatsAppMessageGateway.sendPaymentMethodOptions(phoneNumber);
            case GENERIC_SAFE_FALLBACK -> whatsAppMessageGateway.sendUnknownInputFallback(phoneNumber);
        }
    }

    public void sendStructuredEscape(
            String phoneNumber,
            Conversation conversation,
            StructuredEscapeType structuredEscapeType,
            List<ServiceCatalogItem> availableServices) {
        switch (structuredEscapeType) {
            case GREETING_HELP_BUTTONS -> whatsAppMessageGateway.sendGreeting(phoneNumber);
            case ICP_ADVANCE_TO_DISCOVERY -> whatsAppMessageGateway.sendTextMessage(
                phoneNumber,
                "Sem problema. Vou te ajudar a descobrir a melhor opção da Urba com base no que você precisa agora 😊"
            );
            case SERVICE_DISCOVERY_OPTIONS -> {
                if (conversation.context().slotValue(br.com.urbana.connect.domain.conversation.model.ConversationSlotName.NEEDS_DISCOVERY_HELP)
                    .map(Boolean::parseBoolean)
                    .orElse(true)) {
                    whatsAppMessageGateway.sendGuidedTriageOptions(phoneNumber, availableServices);
                } else {
                    whatsAppMessageGateway.sendDirectTriageOptions(phoneNumber, availableServices);
                }
            }
            case CONFIRMATION_OPTIONS -> availableServices.stream()
                .filter(service -> service.type() == conversation.selectedService())
                .findFirst()
                .ifPresent(service -> whatsAppMessageGateway.sendServicePresentation(phoneNumber, service));
            case TERMS_RETRY -> whatsAppMessageGateway.sendTermsOfUse(phoneNumber);
            case PAYMENT_OPTIONS -> whatsAppMessageGateway.sendPaymentMethodOptions(phoneNumber);
            case GENERIC_HELP -> whatsAppMessageGateway.sendUnknownInputFallback(phoneNumber);
        }
    }
}
