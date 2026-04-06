package br.com.urbana.connect.infrastructure.whatsapp;

import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WhatsAppCloudApiGateway implements WhatsAppMessageGateway {

    private static final String GREETING_TEXT = "Precisando de ajuda para encontrar o serviço perfeito?";
    private static final String TERMS_OF_USE_LINK =
        "https://drive.google.com/file/d/10ZFSwmVHybvuaYTYE4lW5XspLN7tZa67/view?usp=sharing";

    private final RestClient restClient;
    private final String phoneNumberId;
    private final String accessToken;

    public WhatsAppCloudApiGateway(RestClient restClient, String phoneNumberId, String accessToken) {
        this.restClient = restClient;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
    }

    @Override
    public void sendGreeting(String phoneNumber) {
        sendPayload(buildGreetingPayload(phoneNumber));
    }

    @Override
    public void sendGuidedTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices) {
        sendPayload(buildGuidedTriagePayload(phoneNumber, availableServices));
    }

    @Override
    public void sendDirectTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices) {
        sendPayload(buildDirectTriagePayload(phoneNumber, availableServices));
    }

    @Override
    public void sendServicePresentation(String phoneNumber, ServiceCatalogItem selectedService) {
        sendPayload(buildServicePresentationPayload(phoneNumber, selectedService));
    }

    @Override
    public void sendTermsOfUse(String phoneNumber) {
        sendPayload(textPayload(
            phoneNumber,
            "Pra gente iniciar a Decor, o último check é no nosso Termo de Uso 🤝.\n\n"
                + "Assim deixamos tudo transparente e zero dor de cabeça.\n\n"
                + "Dá uma olhadinha nele: 👇🏾\n\n"
                + TERMS_OF_USE_LINK
                + "\n\nDepois da leitura, é só nos responder com a palavra \"Aceito\" e vamos lá começar os trabalhos! 🚀"
        ));
    }

    @Override
    public void sendPaymentMethodOptions(String phoneNumber) {
        sendPayload(Map.of(
            "messaging_product", "whatsapp",
            "to", phoneNumber,
            "type", "interactive",
            "interactive", Map.of(
                "type", "button",
                "body", Map.of("text", "Você irá realizar o pagamento via PIX ou cartão de crédito?"),
                "action", Map.of(
                    "buttons", List.of(
                        replyButton("PAYMENT_PIX", "PIX"),
                        replyButton("PAYMENT_CARD", "Cartão")
                    )
                )
            )
        ));
    }

    @Override
    public void sendPaymentLink(String phoneNumber, ServiceCatalogItem selectedService) {
        sendPayload(textPayload(
            phoneNumber,
            "Vamos lá então!\n\nPara efetuar o pagamento para a *"
                + selectedService.name() + "* " + selectedService.emoji()
                + "\nClique no link abaixo 👇🏾\n"
                + selectedService.paymentLink()
        ));
    }

    @Override
    public void sendClosingMessage(String phoneNumber) {
        sendPayload(textPayload(
            phoneNumber,
            "Perfeito! Assim que o pagamento for confirmado, daremos os próximos passos 😊"
        ));
    }

    @Override
    public void sendHumanHandoffAcknowledgement(String phoneNumber) {
        sendPayload(textPayload(
            phoneNumber,
            "Iremos repassar sua dúvida para nossa equipe, que entrará em contato logo mais"
        ));
    }

    private void sendPayload(Map<String, Object> payload) {
        restClient.post()
            .uri("/v18.0/{phoneNumberId}/messages", phoneNumberId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    private Map<String, Object> buildGreetingPayload(String phoneNumber) {
        return Map.of(
            "messaging_product", "whatsapp",
            "to", phoneNumber,
            "type", "interactive",
            "interactive", Map.of(
                "type", "button",
                "body", Map.of("text", GREETING_TEXT),
                "action", Map.of(
                    "buttons", List.of(
                        replyButton("YES_HELP", "✅ Sim, estou precisando"),
                        replyButton("NO_HELP", "🚫 Não, já sei o que quero")
                    )
                )
            )
        );
    }

    private Map<String, Object> buildGuidedTriagePayload(String phoneNumber, List<ServiceCatalogItem> availableServices) {
        return interactiveListPayload(
            phoneNumber,
            "Das opções abaixo, qual você se identifica mais?",
            "Ver opções",
            availableServices.stream()
                .map(service -> listRow(
                    service.type().name(),
                    service.emoji() + " " + service.name(),
                    service.scenarioText()
                ))
                .toList()
        );
    }

    private Map<String, Object> buildDirectTriagePayload(String phoneNumber, List<ServiceCatalogItem> availableServices) {
        return interactiveListPayload(
            phoneNumber,
            "Então conta pra gente, para qual opção deseja atendimento:",
            "Ver serviços",
            availableServices.stream()
                .map(service -> listRow(
                    service.type().name(),
                    service.emoji() + " " + service.name(),
                    formatPrice(service.price()) + " por ambiente"
                ))
                .toList()
        );
    }

    private Map<String, Object> buildServicePresentationPayload(String phoneNumber, ServiceCatalogItem selectedService) {
        String body = "Acho que encontramos o serviço certo para você! 😃\n\n"
            + selectedService.presentationText()
            + "\n\n"
            + formatPrice(selectedService.price()) + " por ambiente"
            + "\n\nEra isso que você estava buscando?";

        return Map.of(
            "messaging_product", "whatsapp",
            "to", phoneNumber,
            "type", "interactive",
            "interactive", Map.of(
                "type", "button",
                "body", Map.of("text", body),
                "action", Map.of(
                    "buttons", List.of(
                        replyButton("CONFIRM_SERVICE", "✅ Sim, acertou em cheio"),
                        replyButton("RESELECT_SERVICE", "🚫 Não, foi quase")
                    )
                )
            )
        );
    }

    private Map<String, Object> textPayload(String phoneNumber, String body) {
        return Map.of(
            "messaging_product", "whatsapp",
            "to", phoneNumber,
            "type", "text",
            "text", Map.of("body", body)
        );
    }

    private Map<String, Object> interactiveListPayload(
            String phoneNumber,
            String bodyText,
            String buttonText,
            List<Map<String, Object>> rows) {
        return Map.of(
            "messaging_product", "whatsapp",
            "to", phoneNumber,
            "type", "interactive",
            "interactive", Map.of(
                "type", "list",
                "body", Map.of("text", bodyText),
                "action", Map.of(
                    "button", buttonText,
                    "sections", List.of(Map.of("rows", rows))
                )
            )
        );
    }

    private Map<String, Object> listRow(String id, String title, String description) {
        return Map.of(
            "id", id,
            "title", title,
            "description", description
        );
    }

    private Map<String, Object> replyButton(String id, String title) {
        return Map.of(
            "type", "reply",
            "reply", Map.of(
                "id", id,
                "title", title
            )
        );
    }

    private String formatPrice(BigDecimal price) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return formatter.format(price);
    }
}
