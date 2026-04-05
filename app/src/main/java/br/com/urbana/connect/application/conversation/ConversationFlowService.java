package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationFlowService {

    private static final Logger log = LoggerFactory.getLogger(ConversationFlowService.class);

    private final ConversationLifecycleService conversationLifecycleService;
    private final ConversationGateway conversationGateway;
    private final ServiceCatalogGateway serviceCatalogGateway;
    private final WhatsAppMessageGateway whatsAppMessageGateway;

    public ConversationFlowService(
            ConversationLifecycleService conversationLifecycleService,
            ConversationGateway conversationGateway,
            ServiceCatalogGateway serviceCatalogGateway,
            WhatsAppMessageGateway whatsAppMessageGateway) {
        this.conversationLifecycleService = conversationLifecycleService;
        this.conversationGateway = conversationGateway;
        this.serviceCatalogGateway = serviceCatalogGateway;
        this.whatsAppMessageGateway = whatsAppMessageGateway;
    }

    public Conversation handleIncomingMessage(InboundWhatsAppMessage inboundMessage, Instant receivedAt) {
        Conversation conversation = conversationLifecycleService.resumeOrStart(inboundMessage.phoneNumber(), receivedAt);
        List<ServiceCatalogItem> availableServices = serviceCatalogGateway.findAvailable();

        return switch (conversation.currentStep()) {
            case GREETING -> handleGreeting(conversation, inboundMessage, availableServices, receivedAt);
            case TRIAGE_GUIDED -> handleGuidedTriage(conversation, inboundMessage, availableServices, receivedAt);
            case TRIAGE_DIRECT -> handleDirectTriage(conversation, inboundMessage, availableServices, receivedAt);
            case AWAITING_CONFIRMATION -> handleAwaitingConfirmation(conversation, inboundMessage, availableServices, receivedAt);
            case AWAITING_TERMS -> handleAwaitingTerms(conversation, inboundMessage, receivedAt);
            case AWAITING_PAYMENT_METHOD -> handleAwaitingPaymentMethod(conversation, inboundMessage, receivedAt);
            default -> conversation;
        };
    }

    private Conversation handleGreeting(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        String replyId = inboundMessage.interactiveReplyId();

        if ("YES_HELP".equals(replyId)) {
            Conversation updated = conversationGateway.save(conversation.moveTo(ConversationStep.TRIAGE_GUIDED, receivedAt));
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendGuidedTriageOptions(inboundMessage.phoneNumber(), availableServices)
            );
            return updated;
        }

        if ("NO_HELP".equals(replyId)) {
            Conversation updated = conversationGateway.save(conversation.moveTo(ConversationStep.TRIAGE_DIRECT, receivedAt));
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendDirectTriageOptions(inboundMessage.phoneNumber(), availableServices)
            );
            return updated;
        }

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendGreeting(inboundMessage.phoneNumber())
        );
        return conversation;
    }

    private Conversation handleGuidedTriage(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        Optional<ServiceCatalogItem> selectedService = resolveSelectedService(availableServices, inboundMessage.interactiveReplyId());
        if (selectedService.isPresent()) {
            return moveToConfirmation(conversation, inboundMessage.phoneNumber(), selectedService.get(), receivedAt);
        }

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendGuidedTriageOptions(inboundMessage.phoneNumber(), availableServices)
        );
        return conversation;
    }

    private Conversation handleDirectTriage(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        Optional<ServiceCatalogItem> selectedService = resolveSelectedService(availableServices, inboundMessage.interactiveReplyId());
        if (selectedService.isPresent()) {
            return moveToConfirmation(conversation, inboundMessage.phoneNumber(), selectedService.get(), receivedAt);
        }

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendDirectTriageOptions(inboundMessage.phoneNumber(), availableServices)
        );
        return conversation;
    }

    private Conversation moveToConfirmation(
            Conversation conversation,
            String phoneNumber,
            ServiceCatalogItem selectedService,
            Instant receivedAt) {
        Conversation updated = conversationGateway.save(
            conversation.selectService(selectedService.type(), ConversationStep.AWAITING_CONFIRMATION, receivedAt)
        );

        sendSafely(
            phoneNumber,
            updated.currentStep(),
            () -> whatsAppMessageGateway.sendServicePresentation(phoneNumber, selectedService)
        );
        return updated;
    }

    private Conversation handleAwaitingConfirmation(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            Instant receivedAt) {
        String replyId = inboundMessage.interactiveReplyId();

        if ("CONFIRM_SERVICE".equals(replyId)) {
            Conversation updated = conversationGateway.save(conversation.moveTo(ConversationStep.AWAITING_TERMS, receivedAt));
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendTermsOfUse(inboundMessage.phoneNumber())
            );
            return updated;
        }

        if ("RESELECT_SERVICE".equals(replyId)) {
            Conversation updated = conversationGateway.save(conversation.moveTo(ConversationStep.TRIAGE_DIRECT, receivedAt));
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendDirectTriageOptions(inboundMessage.phoneNumber(), availableServices)
            );
            return updated;
        }

        return serviceCatalogGateway.findByType(conversation.selectedService())
            .map(service -> {
                sendSafely(
                    inboundMessage.phoneNumber(),
                    conversation.currentStep(),
                    () -> whatsAppMessageGateway.sendServicePresentation(inboundMessage.phoneNumber(), service)
                );
                return conversation;
            })
            .orElse(conversation);
    }

    private Conversation handleAwaitingTerms(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            Instant receivedAt) {
        if (containsTermsAcceptance(inboundMessage.textBody())) {
            Conversation updated = conversationGateway.save(conversation.moveTo(ConversationStep.AWAITING_PAYMENT_METHOD, receivedAt));
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendPaymentMethodOptions(inboundMessage.phoneNumber())
            );
            return updated;
        }

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendTermsOfUse(inboundMessage.phoneNumber())
        );
        return conversation;
    }

    private Conversation handleAwaitingPaymentMethod(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            Instant receivedAt) {
        String paymentMethod = resolvePaymentMethod(inboundMessage.interactiveReplyId());
        if (paymentMethod != null) {
            Optional<ServiceCatalogItem> selectedService = serviceCatalogGateway.findByType(conversation.selectedService());
            Conversation updated = conversationGateway.save(
                conversation.selectPaymentMethod(paymentMethod, ConversationStep.PAYMENT_LINK_SENT, receivedAt)
            );
            selectedService.ifPresent(service -> sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendPaymentLink(inboundMessage.phoneNumber(), service)
            ));
            sendSafely(
                inboundMessage.phoneNumber(),
                updated.currentStep(),
                () -> whatsAppMessageGateway.sendClosingMessage(inboundMessage.phoneNumber())
            );
            return updated;
        }

        sendSafely(
            inboundMessage.phoneNumber(),
            conversation.currentStep(),
            () -> whatsAppMessageGateway.sendPaymentMethodOptions(inboundMessage.phoneNumber())
        );
        return conversation;
    }

    private Optional<ServiceCatalogItem> resolveSelectedService(List<ServiceCatalogItem> availableServices, String replyId) {
        if (replyId == null || replyId.isBlank()) {
            return Optional.empty();
        }

        try {
            ServiceType selectedType = ServiceType.valueOf(replyId);
            return availableServices.stream()
                .filter(service -> service.type() == selectedType)
                .findFirst();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void sendSafely(String phoneNumber, ConversationStep step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.error(
                "Falha ao enviar mensagem para {} na etapa {}: {}",
                phoneNumber,
                step,
                exception.getMessage()
            );
        }
    }

    private boolean containsTermsAcceptance(String textBody) {
        if (textBody == null || textBody.isBlank()) {
            return false;
        }

        return textBody.toLowerCase().contains("aceito");
    }

    private String resolvePaymentMethod(String replyId) {
        return switch (replyId) {
            case "PAYMENT_PIX" -> "PIX";
            case "PAYMENT_CARD" -> "CARTÃO";
            default -> null;
        };
    }
}
