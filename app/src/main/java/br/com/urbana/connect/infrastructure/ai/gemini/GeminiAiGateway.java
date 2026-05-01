package br.com.urbana.connect.infrastructure.ai.gemini;

import br.com.urbana.connect.domain.conversation.model.AssembledContext;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiAction;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.model.ServiceSummary;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.port.out.AiGateway;
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
    public ConversationalAiReply converse(AssembledContext context) {
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

    private String buildConversationalPrompt(AssembledContext context) {
        String availableServices = context.businessKnowledge().stream()
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
        String recentHistory = context.sessionMemory().isEmpty()
            ? "- sem histórico recente"
            : context.sessionMemory().stream().map(this::sanitize).collect(Collectors.joining("\n"));

        return """
            Você é a Urba, atendente conversacional da Urbana do Brasil.
            Responda SOMENTE em JSON válido.

            Core Identity:
            %s

            Operational Policy:
            %s

            Conversation Playbook da etapa:
            %s

            Etapa atual: %s
            Objetivo da etapa: %s
            Mensagem do cliente: %s
            Histórico recente:
            %s

            Slots atuais:
            %s

            Serviços disponíveis:
            %s

            Regras duras adicionais:
            - Nunca invente serviços, preços, links ou etapas.
            - Faça no máximo uma pergunta por turno.
            - Prefira respostas curtas e humanas.
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
            sanitize(context.coreIdentity()),
            sanitize(context.operationalPolicy()),
            sanitize(context.conversationPlaybook()),
            context.currentStep(),
            sanitize(context.stageGoal()),
            sanitize(context.userMessage()),
            recentHistory,
            serializedSlots.isBlank() ? "- nenhum slot preenchido" : serializedSlots,
            availableServices
        );
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
