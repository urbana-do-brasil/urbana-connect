package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;

import java.util.List;

public interface WhatsAppMessageGateway {

    void sendTextMessage(String phoneNumber, String bodyText);

    /**
     * Sends a text and returns the provider message identifier when the
     * adapter exposes one. Existing presentation-only adapters remain
     * source-compatible and return {@code null}; a durable outbox must then
     * keep the attempt ambiguous rather than claiming success.
     */
    default String sendTextMessageWithResult(String phoneNumber, String bodyText) {
        sendTextMessage(phoneNumber, bodyText);
        return null;
    }

    void sendGreeting(String phoneNumber);

    void sendGuidedTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices);

    void sendDirectTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices);

    void sendServicePresentation(String phoneNumber, ServiceCatalogItem selectedService);

    void sendTermsOfUse(String phoneNumber);

    void sendPaymentMethodOptions(String phoneNumber);

    void sendPaymentLink(String phoneNumber, ServiceCatalogItem selectedService);

    void sendClosingMessage(String phoneNumber);

    void sendHumanHandoffAcknowledgement(String phoneNumber);

    void sendUnknownInputFallback(String phoneNumber);
}
