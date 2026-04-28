package br.com.urbana.connect.infrastructure.whatsapp;

import br.com.urbana.connect.domain.conversation.model.ConversationContentKey;
import br.com.urbana.connect.domain.conversation.model.ConversationMessage;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageType;
import br.com.urbana.connect.domain.conversation.port.out.ConversationContentGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
    private static final String PAYMENT_METHOD_TEXT = "Você irá realizar o pagamento via PIX ou cartão de crédito?";
    private static final String CLOSING_TEXT = "Perfeito! Assim que o pagamento for confirmado, daremos os próximos passos 😊";
    private static final String HUMAN_HANDOFF_ACK = "Iremos repassar sua dúvida para nossa equipe, que entrará em contato logo mais";
    private static final String UNKNOWN_INPUT_FALLBACK = "Não entendi 😊 Por favor, use as opções abaixo:";
    private static final String GUIDED_TRIAGE_PROMPT = "Das opções abaixo, qual você se identifica mais?";
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
    private final ConversationGateway conversationGateway;
    private final ConversationMessageGateway conversationMessageGateway;
    private final ConversationContentGateway conversationContentGateway;

    public WhatsAppCloudApiGateway(RestClient restClient, String phoneNumberId, String accessToken) {
        this(restClient, phoneNumberId, accessToken, null, null, null);
    }

    public WhatsAppCloudApiGateway(
            RestClient restClient,
            String phoneNumberId,
            String accessToken,
            ConversationGateway conversationGateway,
            ConversationMessageGateway conversationMessageGateway,
            ConversationContentGateway conversationContentGateway) {
        this.restClient = restClient;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.conversationGateway = conversationGateway;
        this.conversationMessageGateway = conversationMessageGateway;
        this.conversationContentGateway = conversationContentGateway;
    }

    @Override
    public void sendGreeting(String phoneNumber) {
        String bodyText = resolveContent(ConversationContentKey.GREETING_TEXT, GREETING_TEXT);
        sendPayload(
            buildGreetingPayload(phoneNumber, bodyText),
            phoneNumber,
            "GREETING",
            appendOptions(bodyText, List.of("Preciso de ajuda", "Já sei o que quero")),
            ConversationMessageType.INTERACTIVE_BUTTON
        );
    }

    @Override
    public void sendGuidedTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices) {
        String bodyText = resolveContent(ConversationContentKey.GUIDED_TRIAGE_PROMPT, GUIDED_TRIAGE_PROMPT);
        sendPayload(
            buildGuidedTriagePayload(phoneNumber, availableServices, bodyText),
            phoneNumber,
            "GUIDED_TRIAGE",
            appendOptions(bodyText, availableServices.stream().map(service -> service.emoji() + " " + service.name()).toList()),
            ConversationMessageType.INTERACTIVE_LIST
        );
    }

    @Override
    public void sendDirectTriageOptions(String phoneNumber, List<ServiceCatalogItem> availableServices) {
        String bodyText = resolveContent(ConversationContentKey.DIRECT_TRIAGE_TEXT, DIRECT_TRIAGE_TEXT);
        sendPayload(
            buildDirectTriagePayload(phoneNumber, availableServices, bodyText),
            phoneNumber,
            "DIRECT_TRIAGE",
            appendOptions(bodyText, availableServices.stream()
                .map(service -> service.emoji() + " " + service.name() + " - " + formatPrice(service.price()) + " por ambiente")
                .toList()),
            ConversationMessageType.INTERACTIVE_LIST
        );
    }

    @Override
    public void sendServicePresentation(String phoneNumber, ServiceCatalogItem selectedService) {
        String bodyText = buildServicePresentationBody(selectedService);
        sendPayload(
            buildServicePresentationPayload(phoneNumber, bodyText),
            phoneNumber,
            "SERVICE_PRESENTATION",
            appendOptions(bodyText, List.of("Sim, é isso", "Não, refazer")),
            ConversationMessageType.INTERACTIVE_BUTTON
        );
    }

    @Override
    public void sendTermsOfUse(String phoneNumber) {
        String bodyText = resolveContent(ConversationContentKey.TERMS_TEXT, defaultTermsText())
            .replace("{{TERMS_LINK}}", TERMS_OF_USE_LINK);
        sendPayload(Map.of(
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, INTERACTIVE,
            INTERACTIVE, Map.of(
                TYPE, BUTTON,
                "body", Map.of("text", bodyText),
                ACTION, Map.of(
                    BUTTONS, List.of(
                        replyButton("TERMS_ACCEPT", "Sim"),
                        replyButton("TERMS_DECLINE", "Não")
                    )
                )
            )
        ), phoneNumber, "TERMS_OF_USE", appendOptions(bodyText, List.of("Sim", "Não")), ConversationMessageType.INTERACTIVE_BUTTON);
    }

    @Override
    public void sendPaymentMethodOptions(String phoneNumber) {
        String bodyText = resolveContent(ConversationContentKey.PAYMENT_METHOD_TEXT, PAYMENT_METHOD_TEXT);
        sendPayload(Map.of(
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, INTERACTIVE,
            INTERACTIVE, Map.of(
                TYPE, BUTTON,
                "body", Map.of("text", WhatsAppPayloadConstraints.interactiveBodyText(bodyText)),
                ACTION, Map.of(
                    BUTTONS, List.of(
                        replyButton("PAYMENT_PIX", "PIX"),
                        replyButton("PAYMENT_CARD", "Cartão")
                    )
                )
            )
        ), phoneNumber, "PAYMENT_METHOD_OPTIONS", appendOptions(bodyText, List.of("PIX", "Cartão")), ConversationMessageType.INTERACTIVE_BUTTON);
    }

    @Override
    public void sendPaymentLink(String phoneNumber, ServiceCatalogItem selectedService) {
        String bodyText = "Vamos lá então!\n\nPara efetuar o pagamento para a *"
            + selectedService.name() + "* " + selectedService.emoji()
            + "\nClique no link abaixo 👇🏾\n"
            + selectedService.paymentLink();
        sendPayload(textPayload(phoneNumber, bodyText), phoneNumber, "PAYMENT_LINK", bodyText, ConversationMessageType.TEXT);
    }

    @Override
    public void sendClosingMessage(String phoneNumber) {
        String bodyText = resolveContent(ConversationContentKey.CLOSING_TEXT, CLOSING_TEXT);
        sendPayload(textPayload(phoneNumber, bodyText), phoneNumber, "CLOSING", bodyText, ConversationMessageType.TEXT);
    }

    @Override
    public void sendHumanHandoffAcknowledgement(String phoneNumber) {
        String bodyText = resolveContent(ConversationContentKey.HUMAN_HANDOFF_ACK, HUMAN_HANDOFF_ACK);
        sendPayload(textPayload(phoneNumber, bodyText), phoneNumber, "HUMAN_HANDOFF_ACK", bodyText, ConversationMessageType.TEXT);
    }

    @Override
    public void sendUnknownInputFallback(String phoneNumber) {
        String bodyText = resolveContent(ConversationContentKey.FALLBACK_UNKNOWN_INPUT, UNKNOWN_INPUT_FALLBACK);
        sendPayload(textPayload(phoneNumber, bodyText), phoneNumber, "UNKNOWN_INPUT_FALLBACK", bodyText, ConversationMessageType.TEXT);
    }

    private void sendPayload(
            Map<String, Object> payload,
            String phoneNumber,
            String messageType,
            String visibleText,
            ConversationMessageType conversationMessageType) {
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

            persistOutboundMessage(phoneNumber, visibleText, conversationMessageType);
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

    private Map<String, Object> buildGreetingPayload(String phoneNumber, String bodyText) {
        return Map.of(
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, INTERACTIVE,
            INTERACTIVE, Map.of(
                TYPE, BUTTON,
                "body", Map.of("text", WhatsAppPayloadConstraints.interactiveBodyText(bodyText)),
                ACTION, Map.of(
                    BUTTONS, List.of(
                        replyButton("YES_HELP", "Preciso de ajuda"),
                        replyButton("NO_HELP", "Já sei o que quero")
                    )
                )
            )
        );
    }

    private Map<String, Object> buildGuidedTriagePayload(
            String phoneNumber,
            List<ServiceCatalogItem> availableServices,
            String bodyText) {
        return interactiveListPayload(
            phoneNumber,
            bodyText,
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

    private Map<String, Object> buildDirectTriagePayload(
            String phoneNumber,
            List<ServiceCatalogItem> availableServices,
            String bodyText) {
        return interactiveListPayload(
            phoneNumber,
            bodyText,
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

    private String buildServicePresentationBody(ServiceCatalogItem selectedService) {
        return "Acho que encontramos o serviço certo para você! 😃\n\n"
            + selectedService.presentationText()
            + "\n\n"
            + formatPrice(selectedService.price()) + " por ambiente"
            + "\n\nEra isso que você estava buscando?";
    }

    private Map<String, Object> buildServicePresentationPayload(String phoneNumber, String bodyText) {
        return Map.of(
            MESSAGING_PRODUCT, WHATSAPP,
            "to", phoneNumber,
            TYPE, INTERACTIVE,
            INTERACTIVE, Map.of(
                TYPE, BUTTON,
                "body", Map.of("text", WhatsAppPayloadConstraints.interactiveBodyText(bodyText)),
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

    private String resolveContent(ConversationContentKey key, String fallback) {
        return Optional.ofNullable(conversationContentGateway)
            .flatMap(gateway -> gateway.findActiveValue(key))
            .filter(value -> !value.isBlank())
            .orElse(fallback);
    }

    private String appendOptions(String body, List<String> options) {
        if (options == null || options.isEmpty()) {
            return body;
        }

        return body + "\n\nOpções:\n- " + String.join("\n- ", options);
    }

    private void persistOutboundMessage(String phoneNumber, String visibleText, ConversationMessageType messageType) {
        if (conversationGateway == null || conversationMessageGateway == null) {
            return;
        }

        conversationGateway.findLatestByPhoneNumber(phoneNumber).ifPresent(conversation ->
            conversationMessageGateway.save(ConversationMessage.outbound(
                conversation.id(),
                phoneNumber,
                messageType,
                visibleText,
                Instant.now(),
                conversation.currentStep().name()
            ))
        );
    }

    private String defaultTermsText() {
        return """
            Pra gente iniciar a Decor, o último check é no nosso Termo de Uso 🤝🏾.

            Assim deixamos tudo transparente e zero dor de cabeça.

            Dá uma olhadinha nele: 👇🏾

            {{TERMS_LINK}}

            Depois da leitura, você aceita seguir com o termo?
            """.stripIndent();
    }
}
