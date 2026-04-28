package br.com.urbana.connect.infrastructure.whatsapp;

import br.com.urbana.connect.domain.conversation.port.out.WhatsAppMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WhatsAppCloudApiGateway implements WhatsAppMessageGateway {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudApiGateway.class);
    private static final Locale BRAZILIAN_PORTUGUESE = Locale.of("pt", "BR");
    private static final String GREETING_TEXT =
        "Olá! Tudo bem?\n\n"
            + "Nossas boas-vindas! 💜\n\n"
            + "Sou a Urba e irei te atender hoje. 😃\n\n"
            + "Precisa de ajuda para encontrar o serviço perfeito para você?";
    private static final String DIRECT_TRIAGE_TEXT =
        "Show! Você já sabe o serviço que deseja. 😄\n\n"
            + "Então conta pra gente, para qual opção deseja atendimento:";
    private static final String TERMS_OF_USE_LINK =
        "https://drive.google.com/file/d/10ZFSwmVHybvuaYTYE4lW5XspLN7tZa67/view?usp=sharing";
    private static final String MESSAGING_PRODUCT = "messaging_product";
    private static final String WHATSAPP = "whatsapp";
    private static final String TYPE = "type";
    private static final String INTERACTIVE = "interactive";
    private static final String BUTTON = "button";
    private static final String ACTION = "action";
    private static final String BUTTONS = "buttons";

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
        sendPayload(buildGreetingPayload(phoneNumber), phoneNumber, "GREETING");
    }

    @Override
    public void sendGuidedTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices) {
        sendPayload(buildGuidedTriagePayload(phoneNumber, availableServices), phoneNumber, "GUIDED_TRIAGE");
    }

    @Override
    public void sendDirectTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices) {
        sendPayload(buildDirectTriagePayload(phoneNumber, availableServices), phoneNumber, "DIRECT_TRIAGE");
    }

    @Override
    public void sendServicePresentation(String phoneNumber, ServiceCatalogItem selectedService) {
        sendPayload(buildServicePresentationPayload(phoneNumber, selectedService), phoneNumber, "SERVICE_PRESENTATION");
    }

    @Override
    public void sendTermsOfUse(String phoneNumber) {
        sendPayload(Map.of(
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, INTERACTIVE,
            INTERACTIVE, Map.of(
                TYPE, BUTTON,
                "body", Map.of("text", WhatsAppPayloadConstraints.interactiveBodyText(
                    "Pra gente iniciar a Decor, o último check é no nosso Termo de Uso 🤝🏾.\n\n"
                        + "Assim deixamos tudo transparente e zero dor de cabeça.\n\n"
                        + "Dá uma olhadinha nele: 👇🏾\n\n"
                        + TERMS_OF_USE_LINK
                        + "\n\nDepois da leitura, você aceita seguir com o termo?"
                )),
                ACTION, Map.of(
                    BUTTONS, List.of(
                        replyButton("TERMS_ACCEPT", "Sim"),
                        replyButton("TERMS_DECLINE", "Não")
                    )
                )
            )
        ), phoneNumber, "TERMS_OF_USE");
    }

    @Override
    public void sendPaymentMethodOptions(String phoneNumber) {
        sendPayload(Map.of(
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, INTERACTIVE,
            INTERACTIVE, Map.of(
                TYPE, BUTTON,
                "body", Map.of("text", WhatsAppPayloadConstraints.interactiveBodyText(
                    "Você irá realizar o pagamento via PIX ou cartão de crédito?"
                )),
                ACTION, Map.of(
                    BUTTONS, List.of(
                        replyButton("PAYMENT_PIX", "PIX"),
                        replyButton("PAYMENT_CARD", "Cartão")
                    )
                )
            )
        ), phoneNumber, "PAYMENT_METHOD_OPTIONS");
    }

    @Override
    public void sendPaymentLink(String phoneNumber, ServiceCatalogItem selectedService) {
        sendPayload(textPayload(
            phoneNumber,
            "Vamos lá então!\n\nPara efetuar o pagamento para a *"
                + selectedService.name() + "* " + selectedService.emoji()
                + "\nClique no link abaixo 👇🏾\n"
                + selectedService.paymentLink()
        ), phoneNumber, "PAYMENT_LINK");
    }

    @Override
    public void sendClosingMessage(String phoneNumber) {
        sendPayload(textPayload(
            phoneNumber,
            "Perfeito! Assim que o pagamento for confirmado, daremos os próximos passos 😊"
        ), phoneNumber, "CLOSING");
    }

    @Override
    public void sendHumanHandoffAcknowledgement(String phoneNumber) {
        sendPayload(textPayload(
            phoneNumber,
            "Iremos repassar sua dúvida para nossa equipe, que entrará em contato logo mais"
        ), phoneNumber, "HUMAN_HANDOFF_ACK");
    }

    private void sendPayload(Map<String, Object> payload, String phoneNumber, String messageType) {
        if (log.isInfoEnabled()) {
            log.info("Enviando mensagem WhatsApp: type={} destination={}", messageType, maskPhoneNumber(phoneNumber));
        }
        try {
            restClient.post()
                .uri("/v18.0/{phoneNumberId}/messages", phoneNumberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException exception) {
            String maskedPhoneNumber = maskPhoneNumber(phoneNumber);
            if (log.isErrorEnabled()) {
                log.error(
                    "Falha ao enviar mensagem WhatsApp: type={} destination={} error={}",
                    messageType,
                    maskedPhoneNumber,
                    exception.getMessage()
                );
            }
            throw new IllegalStateException(
                "Falha ao enviar mensagem WhatsApp type=%s destination=%s".formatted(messageType, maskedPhoneNumber),
                exception
            );
        }
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "***";
        }
        if (phoneNumber.length() <= 7) {
            return "***";
        }

        int prefixLength = Math.min(5, phoneNumber.length() - 4);
        return phoneNumber.substring(0, prefixLength) + "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    private Map<String, Object> buildGreetingPayload(String phoneNumber) {
        return Map.of(
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, INTERACTIVE,
            INTERACTIVE, Map.of(
                TYPE, BUTTON,
                "body", Map.of("text", WhatsAppPayloadConstraints.interactiveBodyText(GREETING_TEXT)),
                ACTION, Map.of(
                    BUTTONS, List.of(
                        replyButton("YES_HELP", "Preciso de ajuda"),
                        replyButton("NO_HELP", "Já sei o que quero")
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
            DIRECT_TRIAGE_TEXT,
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
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, INTERACTIVE,
            INTERACTIVE, Map.of(
                TYPE, BUTTON,
                "body", Map.of("text", WhatsAppPayloadConstraints.interactiveBodyText(body)),
                ACTION, Map.of(
                    BUTTONS, List.of(
                        replyButton("CONFIRM_SERVICE", "Sim, é isso"),
                        replyButton("RESELECT_SERVICE", "Não, refazer")
                    )
                )
            )
        );
    }

    private Map<String, Object> textPayload(String phoneNumber, String body) {
        return Map.of(
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, "text",
            "text", Map.of("body", WhatsAppPayloadConstraints.textBody(body))
        );
    }

    private Map<String, Object> interactiveListPayload(
            String phoneNumber,
            String bodyText,
            String buttonText,
            List<Map<String, Object>> rows) {
        return Map.of(
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, INTERACTIVE,
            INTERACTIVE, Map.of(
                TYPE, "list",
                "body", Map.of("text", WhatsAppPayloadConstraints.interactiveBodyText(bodyText)),
                ACTION, Map.of(
                    BUTTON, WhatsAppPayloadConstraints.listButtonText(buttonText),
                    "sections", List.of(Map.of("rows", rows))
                )
            )
        );
    }

    private Map<String, Object> listRow(String id, String title, String description) {
        return Map.of(
            "id", id,
            "title", WhatsAppPayloadConstraints.listRowTitle(title),
            "description", WhatsAppPayloadConstraints.listRowDescription(description)
        );
    }

    private Map<String, Object> replyButton(String id, String title) {
        return Map.of(
            TYPE, "reply",
            "reply", Map.of(
                "id", id,
                "title", WhatsAppPayloadConstraints.replyButtonTitle(title)
            )
        );
    }

    private String formatPrice(BigDecimal price) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(BRAZILIAN_PORTUGUESE);
        return formatter.format(price);
    }
}
