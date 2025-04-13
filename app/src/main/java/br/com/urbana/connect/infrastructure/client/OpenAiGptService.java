package br.com.urbana.connect.infrastructure.client;

import br.com.urbana.connect.domain.model.ConversationContext;
import br.com.urbana.connect.domain.port.output.GptServicePort;
import br.com.urbana.connect.domain.service.PromptBuilderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço de integração com a API do OpenAI GPT.
 */
@Service
@Slf4j
public class OpenAiGptService implements GptServicePort {
    
    private final OpenAiService openAiService;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final ObjectMapper objectMapper;
    private final PromptBuilderService promptBuilderService;
    
    public OpenAiGptService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model,
            @Value("${openai.max-tokens}") int maxTokens,
            @Value("${openai.temperature}") double temperature,
            ObjectMapper objectMapper,
            PromptBuilderService promptBuilderService) {
        
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.objectMapper = objectMapper;
        this.promptBuilderService = promptBuilderService;
        
        log.info("Inicializando serviço OpenAI com modelo: {}", model);
    }
    
    @Override
    public String generateResponse(String conversationHistory, String userMessage, String systemPrompt) {
        log.debug("Gerando resposta com GPT para mensagem: {}", userMessage);
        
        try {
            List<ChatMessage> messages = new ArrayList<>();
            
            // Se systemPrompt contém a string "## Tarefa: Gerar Saudação Inicial", é um prompt de saudação
            // Se contém "## Base de Conhecimento - Perguntas Frequentes", é um prompt de FAQ 
            // Em ambos os casos, o systemPrompt completo já está no formato necessário
            if (systemPrompt != null && (
                    systemPrompt.contains("## Tarefa: Gerar Saudação Inicial") ||
                    systemPrompt.contains("## Base de Conhecimento - Perguntas Frequentes"))) {
                log.debug("Usando prompt especial: {}", 
                        systemPrompt.contains("## Tarefa: Gerar Saudação Inicial") ? "Saudação" : "FAQ");
                
                // Para estes prompts especiais, enviamos tudo como uma única mensagem de usuário
                messages.add(new ChatMessage("user", systemPrompt));
            } else {
                // Fluxo normal/original
                
                // Usar o PromptBuilderService para construir o prompt completo
                String fullPrompt = promptBuilderService.buildPrompt(userMessage, conversationHistory);
                
                // Adicionar prompt do sistema
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    messages.add(new ChatMessage("system", systemPrompt));
                }
                
                // Adicionar o prompt completo como mensagem do usuário
                messages.add(new ChatMessage("user", fullPrompt));
            }
            
            // Criar requisição
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();
            
            // Chamar API e obter resposta
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            
            String response = result.getChoices().get(0).getMessage().getContent();
            log.info("Resposta gerada com sucesso pelo GPT");
            
            return response;
        } catch (Exception e) {
            log.error("Erro ao gerar resposta com GPT: {}", e.getMessage(), e);
            return "Desculpe, tive um problema ao processar sua mensagem. Por favor, tente novamente ou entre em contato com um atendente humano.";
        }
    }
    
    @Override
    public String analyzeIntent(String message) {
        log.debug("Analisando intenção da mensagem: {}", message);
        
        try {
            // Usar o PromptBuilderService para construir o prompt de análise de intenção
            String prompt = promptBuilderService.buildIntentAnalysisPrompt(message);
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", "Você é um analisador de intenções de mensagens."));
            messages.add(new ChatMessage("user", prompt));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(50)
                    .temperature(0.3)
                    .build();
            
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String intent = result.getChoices().get(0).getMessage().getContent().trim();
            
            log.info("Intenção detectada: {}", intent);
            return intent;
        } catch (Exception e) {
            log.error("Erro ao analisar intenção: {}", e.getMessage(), e);
            return "intenção não identificada";
        }
    }
    
    @Override
    public boolean requiresHumanIntervention(String message, String conversationHistory) {
        log.debug("Verificando se mensagem requer intervenção humana: {}", message);
        
        try {
            // Usar o PromptBuilderService para construir o prompt de verificação de intervenção humana
            String prompt = promptBuilderService.buildHumanInterventionPrompt(message, conversationHistory);
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", "Você é um analisador de mensagens para decidir se precisa de intervenção humana."));
            messages.add(new ChatMessage("user", prompt));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(10)
                    .temperature(0.2)
                    .build();
            
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String response = result.getChoices().get(0).getMessage().getContent().trim().toLowerCase();
            
            boolean needsHuman = response.contains("sim");
            log.info("Análise de intervenção humana: {}", needsHuman);
            
            return needsHuman;
        } catch (Exception e) {
            log.error("Erro ao verificar necessidade de intervenção humana: {}", e.getMessage(), e);
            // Em caso de erro, é mais seguro assumir que precisa de intervenção humana
            return true;
        }
    }
    
    @Override
    public List<String> extractEntities(String message) {
        log.debug("Extraindo entidades da mensagem: {}", message);
        
        try {
            // Usar o PromptBuilderService para construir o prompt de extração de entidades
            String prompt = promptBuilderService.buildEntityExtractionPrompt(message);
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", "Você é um extrator de entidades de texto."));
            messages.add(new ChatMessage("user", prompt));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(150)
                    .temperature(0.2)
                    .build();
            
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String jsonResponse = result.getChoices().get(0).getMessage().getContent().trim();
            
            log.info("Entidades extraídas: {}", jsonResponse);
            
            // Tentar converter o JSON em uma lista de strings
            try {
                // Filtrar apenas os valores não vazios do JSON
                return Arrays.stream(jsonResponse.split("\n"))
                        .filter(line -> line.contains(":") && !line.contains("null") && !line.contains("\\[\\]"))
                        .map(line -> line.split(":", 2)[1].trim().replaceAll("[\",\\[\\]]", ""))
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toList());
            } catch (Exception ex) {
                log.warn("Erro ao parsear JSON de entidades: {}", ex.getMessage());
                // Se não conseguir parsear, retorna a string completa
                return List.of(jsonResponse);
            }
        } catch (Exception e) {
            log.error("Erro ao extrair entidades: {}", e.getMessage(), e);
            return List.of();
        }
    }
} 