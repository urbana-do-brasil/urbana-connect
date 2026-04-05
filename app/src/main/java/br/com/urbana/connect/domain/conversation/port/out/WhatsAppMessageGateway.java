package br.com.urbana.connect.domain.conversation.port.out;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;

import java.util.List;

public interface WhatsAppMessageGateway {

    void sendGreeting(String phoneNumber);

    void sendGuidedTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices);

    void sendDirectTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices);

    void sendServicePresentation(String phoneNumber, ServiceCatalogItem selectedService);

    void sendTermsOfUse(String phoneNumber);

    void sendPaymentMethodOptions(String phoneNumber);

    void sendPaymentLink(String phoneNumber, ServiceCatalogItem selectedService);

    void sendClosingMessage(String phoneNumber);
}
