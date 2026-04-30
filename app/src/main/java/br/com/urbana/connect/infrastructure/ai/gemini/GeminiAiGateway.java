package br.com.urbana.connect.infrastructure.ai.gemini;

import br.com.urbana.connect.domain.conversation.model.AiContext;
import br.com.urbana.connect.domain.conversation.model.AiInterpretation;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiAction;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.model.IntentType;
import br.com.urbana.connect.domain.conversation.model.ServiceSummary;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
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
import java.util.stream.Stream;
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

    @Override
    public ConversationalAiReply converse(AiContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY ausente; retornando fallback conversacional para etapa {}", context.currentStep());
            return ConversationalAiReply.fallback("missing_api_key");
        }

        try {
            GeminiResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1beta/models/{model}:generateContent")
                    .queryParam("key", apiKey)
                    .build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GeminiRequest(
                    List.of(new Content(List.of(new Part(buildConversationalPrompt(context))))),
                    new GenerationConfig("application/json")
                ))
                .retrieve()
                .body(GeminiResponse.class);

            String jsonPayload = extractJsonPayload(response);
            if (jsonPayload == null || jsonPayload.isBlank()) {
                return ConversationalAiReply.fallback("empty_payload");
            }

            ConversationalPayload payload = objectMapper.readValue(jsonPayload, ConversationalPayload.class);
            return new ConversationalAiReply(
                payload.replyText(),
                payload.action(),
                payload.slotUpdates(),
                payload.confidence(),
                payload.shouldAdvance(),
                payload.suggestedNextStep(),
                payload.shouldOfferStructuredOptions(),
                payload.fallbackReason()
            );
        } catch (Exception exception) {
            log.error("Falha ao gerar resposta conversacional com Gemini na etapa {}: {}", context.currentStep(), exception.getMessage());
            return ConversationalAiReply.fallback("exception");
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

    private String buildConversationalPrompt(AiContext context) {
        String availableServices = context.availableServices().stream()
            .map(this::formatService)
            .collect(Collectors.joining("\n"));
        String serializedSlots = context.slots().entrySet().stream()
            .map(entry -> "- %s: value=%s, level=%s, source=%s, confidence=%s".formatted(
                entry.getKey().key(),
                sanitize(entry.getValue().value()),
                entry.getValue().level(),
                entry.getValue().source(),
                entry.getValue().confidence()
            ))
            .collect(Collectors.joining("\n"));

        return """
            Você é a Urba, uma assistente conversacional de atendimento inicial.
            Sua tarefa é responder SOMENTE em JSON válido, seguindo o contrato abaixo.
            Você está dentro de uma state machine. Nunca invente serviços, preços, links ou etapas.

            Etapa atual: %s
            Objetivo da etapa: %s
            Mensagem do cliente: %s
            Histórico recente:
            %s

            Slots atuais:
            %s

            Serviços disponíveis:
            %s

            Regras duras:
            - Em GREETING, descubra se o cliente precisa de ajuda para encontrar o serviço ou se já sabe o que quer.
            - Em ICP_QUALIFICATION, converse de forma natural para coletar pronome de tratamento, primeira experiência e ocupação, sem travar a conversa se a pessoa não quiser responder tudo.
            - Em SERVICE_DISCOVERY, ajude a descobrir qual serviço faz mais sentido e nunca invente serviço fora do catálogo.
            - Só use suggestedNextStep quando realmente fizer sentido avançar.
            - Só marque shouldAdvance=true quando houver dados suficientes para a etapa atual.
            - Se estiver insegura, faça pergunta de esclarecimento e mantenha shouldAdvance=false.
            - Se quiser oferecer botões ou lista como apoio, marque shouldOfferStructuredOptions=true.

            Responda no formato:
            {
              "replyText": "string",
              "action": "ASK_CLARIFYING_QUESTION|CONFIRM_UNDERSTANDING|PROPOSE_SERVICE|OFFER_STRUCTURED_OPTIONS|ACKNOWLEDGE_AND_ADVANCE|REPEAT_WITH_REFRAME|REQUEST_HUMAN_HANDOFF",
              "slotUpdates": [
                {
                  "slot": "needsDiscoveryHelp|pronounPreference|firstTimeHiringDesigner|occupation|suggestedService|confirmedService|termsAccepted|paymentMethod",
                  "value": "string",
                  "level": "TENTATIVE|CONFIRMED",
                  "confidence": 0.0,
                  "source": "INFERRED|EXPLICIT"
                }
              ],
              "confidence": 0.0,
              "shouldAdvance": false,
              "suggestedNextStep": "GREETING|ICP_QUALIFICATION|SERVICE_DISCOVERY|AWAITING_CONFIRMATION|null",
              "shouldOfferStructuredOptions": false,
              "fallbackReason": null
            }
            """.formatted(
            context.currentStep(),
            stageObjective(context.currentStep()),
            sanitize(context.userMessage()),
            sanitize(context.conversationHistory()),
            serializedSlots.isBlank() ? "- nenhum slot preenchido" : serializedSlots,
            availableServices
        );
    }

    private String stageObjective(ConversationStep step) {
        return switch (step) {
            case GREETING -> "entender se o cliente precisa de ajuda para descobrir o serviço";
            case ICP_QUALIFICATION -> "coletar contexto pessoal leve para humanizar e qualificar a conversa";
            case SERVICE_DISCOVERY, TRIAGE_DIRECT, TRIAGE_GUIDED ->
                "descobrir ou confirmar qual serviço do catálogo melhor atende o cliente";
            default -> "respeitar o fluxo atual sem inventar informação";
        };
    }

    private String extractJsonPayload(GeminiResponse response) {
        return Optional.ofNullable(response)
            .map(GeminiResponse::candidates)
            .filter(candidates -> !candidates.isEmpty())
            .map(List::getFirst)
            .map(Candidate::content)
            .map(ContentResponse::parts)
            .filter(parts -> !parts.isEmpty())
            .map(List::getFirst)
            .map(PartResponse::text)
            .orElse(null);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConversationalPayload(
            String replyText,
            ConversationalAiAction action,
            List<ConversationSlotUpdate> slotUpdates,
            Double confidence,
            boolean shouldAdvance,
            ConversationStep suggestedNextStep,
            boolean shouldOfferStructuredOptions,
            String fallbackReason) {
    }
}
