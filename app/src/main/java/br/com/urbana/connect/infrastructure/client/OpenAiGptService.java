package br.com.urbana.connect.infrastructure.client;

import br.com.urbana.connect.domain.port.output.GptServicePort;
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
    
    public OpenAiGptService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model,
            @Value("${openai.max-tokens}") int maxTokens,
            @Value("${openai.temperature}") double temperature,
            ObjectMapper objectMapper) {
        
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.objectMapper = objectMapper;
        
        log.info("Inicializando serviço OpenAI com modelo: {}", model);
    }
    
    @Override
    @Cacheable(value = "gpt-responses", key = "#userMessage + #systemPrompt")
    public String generateResponse(List<String> conversationHistory, String userMessage, String systemPrompt) {
        log.debug("Gerando resposta com GPT para mensagem: {}", userMessage);
        
        try {
            List<ChatMessage> messages = new ArrayList<>();
            
            // Adicionar prompt do sistema
            messages.add(new ChatMessage("system", systemPrompt));
            
            // Adicionar histórico de conversa
            for (String message : conversationHistory) {
                String role = message.startsWith("Cliente: ") ? "user" : "assistant";
                String content = message.startsWith("Cliente: ") 
                        ? message.substring("Cliente: ".length()) 
                        : message.substring("Assistente: ".length());
                
                messages.add(new ChatMessage(role, content));
            }
            
            // Adicionar mensagem atual do usuário
            messages.add(new ChatMessage("user", userMessage));
            
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
            String prompt = "Analise a seguinte mensagem de um cliente e identifique a intenção principal em uma única palavra " +
                    "ou frase curta. Exemplos: 'dúvida sobre coleta', 'reclamação', 'agendar serviço', 'solicitar informação', etc.\n\n" +
                    "Mensagem: " + message + "\n\nIntenção:";
            
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
    public boolean requiresHumanIntervention(String message, String conversationContext) {
        log.debug("Verificando se mensagem requer intervenção humana: {}", message);
        
        try {
            String prompt = "Analise a seguinte mensagem de um cliente e determine se ela requer intervenção " +
                    "humana. Responda apenas com 'sim' ou 'não'.\n\n" +
                    "Considere que requer intervenção humana se:\n" +
                    "1. O cliente explicitamente pede para falar com um atendente humano\n" +
                    "2. A mensagem contém reclamação grave ou urgente\n" +
                    "3. O cliente demonstra insatisfação com as respostas automáticas\n" +
                    "4. O assunto é complexo e provavelmente requer análise humana\n\n" +
                    "Contexto da conversa: " + (conversationContext != null ? conversationContext : "Não disponível") + "\n" +
                    "Mensagem: " + message + "\n\nRequer intervenção humana?";
            
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
            String prompt = "Extraia da seguinte mensagem de um cliente quaisquer entidades relevantes como nomes de pessoas, " +
                    "endereços, tipos de serviço, datas, números de telefone, emails, etc. Liste cada entidade em uma linha " +
                    "separada com o formato 'tipo: valor'.\n\n" +
                    "Mensagem: " + message + "\n\nEntidades:";
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", "Você é um extrator de entidades de texto."));
            messages.add(new ChatMessage("user", prompt));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(200)
                    .temperature(0.3)
                    .build();
            
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String response = result.getChoices().get(0).getMessage().getContent().trim();
            
            List<String> entities = Arrays.stream(response.split("\n"))
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.toList());
            
            log.info("Entidades extraídas: {}", entities);
            return entities;
        } catch (Exception e) {
            log.error("Erro ao extrair entidades: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
} 