package br.com.urbana.connect.infrastructure.client;

import br.com.urbana.connect.domain.service.PromptBuilderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenAiGptServiceTest {

    @Mock
    private OpenAiService openAiService;

    @Mock
    private PromptBuilderService promptBuilderService;

    @Mock
    private ObjectMapper objectMapper;

    private OpenAiGptService openAiGptService;

    private static final String FALLBACK_MESSAGE = "Ops! 😅 Parece que meu cérebro digital deu uma pequena pausa aqui... 🧠 Poderia tentar me perguntar de novo, talvez com outras palavras? Se não der certo, me avisa que eu chamo reforços humanos! 😉";
    private static final String VALID_RESPONSE = "Esta é uma resposta válida do modelo GPT";
    private static final String EMPTY_RESPONSE = "";
    private static final String CONVERSATION_HISTORY = "Histórico da conversa";
    private static final String USER_MESSAGE = "Olá, como vai?";
    private static final String SYSTEM_PROMPT = "Você é Urba, assistente virtual da Urbana do Brasil";

    // Classe estática para permitir testes
    static class TestableOpenAiGptService extends OpenAiGptService {
        public TestableOpenAiGptService(
            String apiKey, String model, int maxTokens, double temperature,
            ObjectMapper objectMapper, PromptBuilderService promptBuilderService) {
            super(apiKey, model, maxTokens, temperature, objectMapper, promptBuilderService);
        }
        
        // Métodos para facilitar testes
        public void setOpenAiService(OpenAiService openAiService) {
            try {
                java.lang.reflect.Field field = OpenAiGptService.class.getDeclaredField("openAiService");
                field.setAccessible(true);
                field.set(this, openAiService);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set openAiService", e);
            }
        }
    }

    @BeforeEach
    void setUp() {
        // Criar instância da versão testável
        openAiGptService = new TestableOpenAiGptService(
            "dummy-api-key", // API key
            "gpt-3.5-turbo",  // model
            1024,            // maxTokens
            0.7,             // temperature
            objectMapper,
            promptBuilderService
        );
        
        // Substituir o openAiService pelo mock
        ((TestableOpenAiGptService) openAiGptService).setOpenAiService(openAiService);
    }

    @Test
    void generateResponse_whenOpenAiServiceThrowsException_shouldReturnFallbackMessage() {
        // Configurar o mock do promptBuilderService
        when(promptBuilderService.buildPrompt(anyString(), anyString())).thenReturn("prompt completo");
        
        // Simular exceção na chamada à API da OpenAI
        when(openAiService.createChatCompletion(any(ChatCompletionRequest.class)))
                .thenThrow(new RuntimeException("Erro simulado da API"));
        
        // Executar o método sob teste
        String result = openAiGptService.generateResponse(CONVERSATION_HISTORY, USER_MESSAGE, SYSTEM_PROMPT);
        
        // Verificar o resultado
        assertEquals(FALLBACK_MESSAGE, result);
        
        // Verificar se a API foi chamada (máximo 3 vezes devido ao retry)
        verify(openAiService, atMost(3)).createChatCompletion(any(ChatCompletionRequest.class));
    }

    @Test
    void generateResponse_whenOpenAiServiceReturnsEmptyResponse_shouldReturnFallbackMessage() {
        // Configurar o mock do promptBuilderService
        when(promptBuilderService.buildPrompt(anyString(), anyString())).thenReturn("prompt completo");
        
        // Configurar o resultado com resposta vazia
        ChatCompletionResult mockResult = createMockCompletionResult(EMPTY_RESPONSE);
        when(openAiService.createChatCompletion(any(ChatCompletionRequest.class))).thenReturn(mockResult);
        
        // Executar o método sob teste
        String result = openAiGptService.generateResponse(CONVERSATION_HISTORY, USER_MESSAGE, SYSTEM_PROMPT);
        
        // Verificar o resultado
        assertEquals(FALLBACK_MESSAGE, result);
    }

    @Test
    void generateResponse_whenOpenAiServiceReturnsValidResponse_shouldReturnThatResponse() {
        // Configurar o mock do promptBuilderService
        when(promptBuilderService.buildPrompt(anyString(), anyString())).thenReturn("prompt completo");
        
        // Configurar o resultado com resposta válida
        ChatCompletionResult mockResult = createMockCompletionResult(VALID_RESPONSE);
        when(openAiService.createChatCompletion(any(ChatCompletionRequest.class))).thenReturn(mockResult);
        
        // Executar o método sob teste
        String result = openAiGptService.generateResponse(CONVERSATION_HISTORY, USER_MESSAGE, SYSTEM_PROMPT);
        
        // Verificar o resultado
        assertEquals(VALID_RESPONSE, result);
    }
    
    @Test
    void requiresHumanIntervention_whenContainsHumanKeywords_shouldReturnTrue() {
        // O método testará a detecção de palavras-chave para intervenção humana
        String messageWithKeyword = "Gostaria de falar com um atendente humano, por favor";
        
        // Executar o método sob teste
        boolean result = openAiGptService.requiresHumanIntervention(messageWithKeyword, CONVERSATION_HISTORY);
        
        // Verificar o resultado
        assertTrue(result);
        
        // Verificar que a API não foi chamada (porque a keyword já resolve)
        verify(openAiService, never()).createChatCompletion(any(ChatCompletionRequest.class));
    }
    
    @Test
    void requiresHumanIntervention_whenApiThrowsException_shouldReturnTrue() {
        // Configurar o mock do promptBuilderService
        when(promptBuilderService.buildHumanInterventionPrompt(anyString(), anyString())).thenReturn("prompt de intervenção");
        
        // Simular exceção na chamada à API
        when(openAiService.createChatCompletion(any(ChatCompletionRequest.class)))
                .thenThrow(new RuntimeException("Erro simulado da API"));
        
        // Executar o método sob teste
        boolean result = openAiGptService.requiresHumanIntervention("Mensagem normal", CONVERSATION_HISTORY);
        
        // Em caso de erro, é mais seguro assumir que precisa de intervenção
        assertTrue(result);
    }

    @Test
    void extractEntities_whenApiThrowsException_shouldReturnEmptyList() {
        // Configurar o mock do promptBuilderService
        when(promptBuilderService.buildEntityExtractionPrompt(anyString())).thenReturn("prompt de extração");
        
        // Simular exceção na chamada à API
        when(openAiService.createChatCompletion(any(ChatCompletionRequest.class)))
                .thenThrow(new RuntimeException("Erro simulado da API"));
        
        // Executar o método sob teste
        List<String> result = openAiGptService.extractEntities("Quero decorar meu apartamento");
        
        // Verificar que uma lista vazia é retornada
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // Método auxiliar para criar um resultado de chat completion
    private ChatCompletionResult createMockCompletionResult(String content) {
        // Criar um mock do resultado da API
        ChatCompletionResult result = mock(ChatCompletionResult.class);
        
        // Criar um mock do ChatCompletionChoice
        ChatCompletionChoice choice = mock(ChatCompletionChoice.class);
        
        // Criar a mensagem de chat
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(content);
        chatMessage.setRole("assistant");
        
        // Configurar o mock do choice para retornar a mensagem
        when(choice.getMessage()).thenReturn(chatMessage);
        
        // Configurar o mock do resultado para retornar uma lista com o choice
        List<ChatCompletionChoice> choices = new ArrayList<>();
        choices.add(choice);
        when(result.getChoices()).thenReturn(choices);
        
        return result;
    }
} 