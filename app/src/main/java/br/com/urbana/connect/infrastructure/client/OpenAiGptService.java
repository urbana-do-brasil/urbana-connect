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
    
    // Mensagens de Fallback no estilo "Urba"
    private static final String FALLBACK_MESSAGE = "Ops! 😅 Parece que meu cérebro digital deu uma pequena pausa aqui... 🧠 Poderia tentar me perguntar de novo, talvez com outras palavras? Se não der certo, me avisa que eu chamo reforços humanos! 😉";
    private static final String FALLBACK_INTENT = "intenção não identificada";
    private static final List<String> FALLBACK_ENTITIES = List.of();
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 1000;
    
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
        
        // Implementação de retry para resiliência
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
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
                
                // Verificar se a resposta é válida (não vazia ou muito curta)
                if (response == null || response.trim().isEmpty() || response.trim().length() < 5) {
                    log.warn("Resposta da API vazia ou muito curta: '{}'", response);
                    if (attempt < MAX_RETRIES) {
                        log.info("Tentando novamente ({}/{})", attempt + 1, MAX_RETRIES);
                        Thread.sleep(RETRY_DELAY_MS);
                        continue;
                    }
                    return FALLBACK_MESSAGE;
                }
                
                log.info("Resposta gerada com sucesso pelo GPT");
                return response;
                
            } catch (InterruptedException e) {
                // Restaurar flag de interrupção
                Thread.currentThread().interrupt();
                log.error("Processo interrompido ao gerar resposta: {}", e.getMessage(), e);
                return FALLBACK_MESSAGE;
                
            } catch (Exception e) {
                log.error("Erro ao gerar resposta com GPT (tentativa {}/{}): {}", 
                        attempt + 1, MAX_RETRIES + 1, e.getMessage(), e);
                
                // Se ainda temos tentativas disponíveis, esperar e tentar novamente
                if (attempt < MAX_RETRIES) {
                    try {
                        log.info("Aguardando {} ms antes de tentar novamente", RETRY_DELAY_MS);
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrompido durante espera entre tentativas", ie);
                        return FALLBACK_MESSAGE;
                    }
                } else {
                    // Esgotamos as tentativas, retornar mensagem de fallback
                    return FALLBACK_MESSAGE;
                }
            }
        }
        
        // Se chegamos aqui, todas as tentativas falharam
        return FALLBACK_MESSAGE;
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
            
            // Validar se a intenção não está vazia
            if (intent == null || intent.trim().isEmpty()) {
                log.warn("Intenção detectada vazia, usando fallback");
                return FALLBACK_INTENT;
            }
            
            log.info("Intenção detectada: {}", intent);
            return intent;
        } catch (Exception e) {
            log.error("Erro ao analisar intenção: {}", e.getMessage(), e);
            return FALLBACK_INTENT;
        }
    }
    
    @Override
    public boolean requiresHumanIntervention(String message, String conversationHistory) {
        log.debug("Verificando se mensagem requer intervenção humana: {}", message);
        
        try {
            // Verificar palavras-chave específicas para atendimento humano
            String normalizedMessage = message.toLowerCase().trim();
            if (containsHumanRequestKeywords(normalizedMessage)) {
                log.info("Palavras-chave de solicitação humana detectadas na mensagem");
                return true;
            }
            
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
    
    /**
     * Verifica se a mensagem contém palavras-chave que indicam explicitamente 
     * um desejo de falar com atendimento humano.
     *
     * @param message Mensagem normalizada (lowercase e trim)
     * @return true se contém palavras-chave de solicitação humana
     */
    private boolean containsHumanRequestKeywords(String message) {
        // Lista de palavras-chave que indicam pedido de atendimento humano
        List<String> humanKeywords = Arrays.asList(
                "falar com atendente", "falar com humano", "atendente humano", 
                "quero atendente", "quero falar com pessoa", "falar com pessoa",
                "atendente por favor", "preciso de atendente", "pessoa real",
                "quero falar com alguém", "falar com gente", "atendimento humano",
                "quero falar com atendente", "por favor atendente", "chat humano",
                "pessoa de verdade", "sem bot", "não quero falar com robô"
        );
        
        return humanKeywords.stream().anyMatch(message::contains);
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
            return FALLBACK_ENTITIES;
        }
    }
} 