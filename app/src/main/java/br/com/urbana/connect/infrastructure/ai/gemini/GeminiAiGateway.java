package br.com.urbana.connect.infrastructure.ai.gemini;

import br.com.urbana.connect.domain.conversation.model.AiContext;
import br.com.urbana.connect.domain.conversation.model.AiInterpretation;
import br.com.urbana.connect.domain.conversation.model.IntentType;
import br.com.urbana.connect.domain.conversation.model.ServiceSummary;
import br.com.urbana.connect.domain.conversation.port.out.AiGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GeminiAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiGateway.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiAiGateway(RestClient restClient, ObjectMapper objectMapper, String apiKey, String model) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public AiInterpretation interpret(AiContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY ausente; retornando UNKNOWN para etapa {}", context.currentStep());
            return AiInterpretation.unknown();
        }

        try {
            GeminiResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1beta/models/{model}:generateContent")
                    .queryParam("key", apiKey)
                    .build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GeminiRequest(
                    List.of(new Content(List.of(new Part(buildPrompt(context))))),
                    new GenerationConfig("application/json")
                ))
                .retrieve()
                .body(GeminiResponse.class);

            String jsonPayload = Optional.ofNullable(response)
                .map(GeminiResponse::candidates)
                .filter(candidates -> !candidates.isEmpty())
                .map(List::getFirst)
                .map(Candidate::content)
                .map(ContentResponse::parts)
                .filter(parts -> !parts.isEmpty())
                .map(List::getFirst)
                .map(PartResponse::text)
                .orElse(null);

            if (jsonPayload == null || jsonPayload.isBlank()) {
                return AiInterpretation.unknown();
            }

            GeminiInterpretationPayload payload = objectMapper.readValue(jsonPayload, GeminiInterpretationPayload.class);
            return new AiInterpretation(
                payload.intent() != null ? payload.intent() : IntentType.UNKNOWN,
                payload.selectedService(),
                payload.suggestedResponse()
            );
        } catch (Exception exception) {
            log.error("Falha ao interpretar mensagem com Gemini na etapa {}: {}", context.currentStep(), exception.getMessage());
            return AiInterpretation.unknown();
        }
    }

    private String buildPrompt(AiContext context) {
        String availableServices = context.availableServices().stream()
            .map(this::formatService)
            .collect(Collectors.joining("\n"));

        return """
            Você é um classificador de intenção da Urba.
            Responda SOMENTE em JSON válido.

            Intents permitidos:
            - SERVICE_SELECTION
            - AFFIRMATION
            - NEGATION
            - TERMS_ACCEPTANCE
            - UNKNOWN

            Regras:
            - Se o cliente escolher ou descrever claramente um serviço do catálogo, retorne SERVICE_SELECTION e selectedService.
            - Se a mensagem significar "sim", retorne AFFIRMATION.
            - Se a mensagem significar "não", retorne NEGATION.
            - Se a mensagem indicar aceite de termos, retorne TERMS_ACCEPTANCE.
            - Se houver dúvida razoável, retorne UNKNOWN.
            - Nunca invente selectedService.

            Etapa atual: %s
            Mensagem do cliente: %s
            Histórico resumido: %s
            Serviços disponíveis:
            %s

            Formato de resposta:
            {
              "intent": "SERVICE_SELECTION|AFFIRMATION|NEGATION|TERMS_ACCEPTANCE|UNKNOWN",
              "selectedService": "DECOR|DECOR_PINTURA|DECOR_FACHADA|DECOR_REFORMA|null",
              "suggestedResponse": null
            }
            """.formatted(
            context.currentStep(),
            sanitize(context.userMessage()),
            sanitize(context.conversationHistory()),
            availableServices
        );
    }

    private String formatService(ServiceSummary service) {
        return "- %s (%s): %s".formatted(service.name(), service.type(), service.scenarioText());
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace("\"", "'");
    }

    private record GeminiRequest(
            List<Content> contents,
            GenerationConfig generationConfig) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text) {
    }

    private record GenerationConfig(String responseMimeType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiResponse(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(ContentResponse content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentResponse(List<PartResponse> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PartResponse(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiInterpretationPayload(
            IntentType intent,
            ServiceType selectedService,
            String suggestedResponse) {
    }
}
