package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.application.config.ContextConfig;
import br.com.urbana.connect.domain.model.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptBuilderServiceTest {

    private static final String DEFAULT_SYSTEM_PROMPT = "Você é um assistente virtual da Urbana do Brasil, especialista em coleta de resíduos e limpeza urbana.";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withLocale(new Locale("pt", "BR"));

    @Spy
    @InjectMocks
    private PromptBuilderService promptBuilderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(promptBuilderService, "defaultSystemPrompt", DEFAULT_SYSTEM_PROMPT);
    }

    @Test
    void buildPrompt_withBasicParams_shouldReturnCorrectPrompt() {
        // Given
        String userMessage = "Olá, gostaria de saber o horário de coleta no meu bairro";
        String conversationHistory = "[USUARIO]: Mensagem anterior\n[ASSISTENTE]: Resposta anterior";

        // When
        String prompt = promptBuilderService.buildPrompt(userMessage, conversationHistory);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains(DEFAULT_SYSTEM_PROMPT);
        assertThat(prompt).contains("### Histórico da conversa:");
        assertThat(prompt).contains(conversationHistory);
        assertThat(prompt).contains("### Mensagem atual:");
        assertThat(prompt).contains(userMessage);
    }

    @Test
    void buildPrompt_withEmptyHistory_shouldOmitHistorySection() {
        // Given
        String userMessage = "Olá, gostaria de saber o horário de coleta no meu bairro";
        String emptyHistory = "";

        // When
        String prompt = promptBuilderService.buildPrompt(userMessage, emptyHistory);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains(DEFAULT_SYSTEM_PROMPT);
        assertThat(prompt).doesNotContain("### Histórico da conversa:");
        assertThat(prompt).contains("### Mensagem atual:");
        assertThat(prompt).contains(userMessage);
    }

    @Test
    void buildPrompt_withNullHistory_shouldOmitHistorySection() {
        // Given
        String userMessage = "Olá, gostaria de saber o horário de coleta no meu bairro";

        // When
        String prompt = promptBuilderService.buildPrompt(userMessage, null);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains(DEFAULT_SYSTEM_PROMPT);
        assertThat(prompt).doesNotContain("### Histórico da conversa:");
        assertThat(prompt).contains("### Mensagem atual:");
        assertThat(prompt).contains(userMessage);
    }

    @Test
    void buildPrompt_withContext_shouldIncludeContextSection() {
        // Given
        String userMessage = "Quando é a próxima coleta?";
        String conversationHistory = "[USUARIO]: Olá\n[ASSISTENTE]: Como posso ajudar?";
        LocalDateTime lastInteraction = LocalDateTime.now();
        
        ConversationContext context = ConversationContext.builder()
                .customerIntent("DUVIDA_SERVICO")
                .lastDetectedTopic("coleta de lixo")
                .identifiedEntities(Arrays.asList("coleta", "horário"))
                .conversationState("AWAITING_RESPONSE")
                .lastInteractionTime(lastInteraction)
                .conversationSummary("Cliente quer saber sobre horários de coleta")
                .build();

        // When
        String prompt = promptBuilderService.buildPrompt(userMessage, conversationHistory, context);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains(DEFAULT_SYSTEM_PROMPT);
        assertThat(prompt).contains("### Informações de contexto:");
        assertThat(prompt).contains("- Tópico atual: coleta de lixo");
        assertThat(prompt).contains("- Intenção do cliente: DUVIDA_SERVICO");
        assertThat(prompt).contains("- Entidades mencionadas: coleta, horário");
        assertThat(prompt).contains("- Estado da conversa: AWAITING_RESPONSE");
        assertThat(prompt).contains("- Última interação em: " + DATE_FORMATTER.format(lastInteraction));
        assertThat(prompt).contains("- Resumo da conversa: Cliente quer saber sobre horários de coleta");
        assertThat(prompt).contains(conversationHistory);
        assertThat(prompt).contains(userMessage);
    }

    @Test
    void buildPrompt_withContextMissingFields_shouldIncludeOnlyAvailableFields() {
        // Given
        String userMessage = "Quando é a próxima coleta?";
        String conversationHistory = "[USUARIO]: Olá\n[ASSISTENTE]: Como posso ajudar?";
        
        ConversationContext context = ConversationContext.builder()
                .customerIntent("DUVIDA_SERVICO")
                .build();

        // When
        String prompt = promptBuilderService.buildPrompt(userMessage, conversationHistory, context);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("### Informações de contexto:");
        assertThat(prompt).contains("- Intenção do cliente: DUVIDA_SERVICO");
        assertThat(prompt).doesNotContain("- Tópico atual:");
        assertThat(prompt).doesNotContain("- Entidades mencionadas:");
        assertThat(prompt).doesNotContain("- Estado da conversa:");
        assertThat(prompt).doesNotContain("- Última interação em:");
        assertThat(prompt).doesNotContain("- Resumo da conversa:");
    }

    @Test
    void buildPrompt_withHumanInterventionContext_shouldCustomizeSystemPrompt() {
        // Given
        String userMessage = "Quero falar com um humano agora!";
        String conversationHistory = "[USUARIO]: Estou com problemas\n[ASSISTENTE]: Como posso ajudar?";
        
        ConversationContext context = ConversationContext.builder()
                .needsHumanIntervention(true)
                .build();

        // When
        String prompt = promptBuilderService.buildPrompt(userMessage, conversationHistory, context);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains(DEFAULT_SYSTEM_PROMPT);
        assertThat(prompt).contains("Percebo que esta conversa pode requerer atendimento humano em breve");
    }

    @Test
    void buildIntentAnalysisPrompt_shouldReturnCorrectlyFormattedPrompt() {
        // Given
        String userMessage = "Gostaria de saber quanto custa o serviço de coleta para empresas";

        // When
        String prompt = promptBuilderService.buildIntentAnalysisPrompt(userMessage);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("Analise a seguinte mensagem");
        assertThat(prompt).contains("\"" + userMessage + "\"");
        assertThat(prompt).contains("DUVIDA_SERVICO");
        assertThat(prompt).contains("PRECO_PAGAMENTO");
        assertThat(prompt).contains("Identifique a principal intenção do usuário");
    }

    @Test
    void buildHumanInterventionPrompt_withHistoryAndMessage_shouldReturnCorrectlyFormattedPrompt() {
        // Given
        String userMessage = "Isso é um absurdo! Quero falar com um humano!";
        String conversationHistory = "[USUARIO]: Não estou conseguindo resolver\n[ASSISTENTE]: Posso tentar ajudar";

        // When
        String prompt = promptBuilderService.buildHumanInterventionPrompt(userMessage, conversationHistory);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("analisador especializado");
        assertThat(prompt).contains("## Histórico da conversa:");
        assertThat(prompt).contains(conversationHistory);
        assertThat(prompt).contains("## Mensagem atual do usuário:");
        assertThat(prompt).contains(userMessage);
        assertThat(prompt).contains("SIM");
        assertThat(prompt).contains("NÃO");
    }

    @Test
    void buildHumanInterventionPrompt_withNullHistory_shouldOmitHistorySection() {
        // Given
        String userMessage = "Preciso de ajuda urgente";

        // When
        String prompt = promptBuilderService.buildHumanInterventionPrompt(userMessage, null);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("analisador especializado");
        assertThat(prompt).doesNotContain("## Histórico da conversa:");
        assertThat(prompt).contains("## Mensagem atual do usuário:");
        assertThat(prompt).contains(userMessage);
    }

    @Test
    void buildEntityExtractionPrompt_shouldReturnCorrectlyFormattedPrompt() {
        // Given
        String userMessage = "Meu nome é João Silva, moro na Rua das Flores, 123, bairro Jardim, São Paulo, telefone (11) 98765-4321";

        // When
        String prompt = promptBuilderService.buildEntityExtractionPrompt(userMessage);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("extração de entidades");
        assertThat(prompt).contains("\"" + userMessage + "\"");
        assertThat(prompt).contains("Extraia entidades relevantes");
        assertThat(prompt).contains("formato JSON");
        assertThat(prompt).contains("nome:");
        assertThat(prompt).contains("endereco:");
        assertThat(prompt).contains("bairro:");
        assertThat(prompt).contains("telefone:");
    }

    @Test
    void buildSummaryPrompt_shouldReturnCorrectlyFormattedPrompt() {
        // Given
        String conversationHistory = """
                [USUARIO]: Olá, gostaria de informações sobre coleta de lixo
                [ASSISTENTE]: Olá! Temos serviços de coleta para residências e empresas. Como posso ajudar?
                [USUARIO]: Quanto custa para uma residência pequena?
                [ASSISTENTE]: Para residências pequenas, o custo é de R$50,00 mensais com coleta semanal.
                [USUARIO]: E para empresas?
                [ASSISTENTE]: Para empresas, os preços começam em R$300,00, variando conforme o volume e tipo de resíduos.
                """;

        // When
        String prompt = promptBuilderService.buildSummaryPrompt(conversationHistory);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("resumir conversas");
        assertThat(prompt).contains("Crie um resumo claro e conciso");
        assertThat(prompt).contains(conversationHistory);
    }

    @Test
    void buildSummaryPrompt_withEmptyConversation_shouldStillFormatPromptCorrectly() {
        // Given
        String emptyConversation = "";

        // When
        String prompt = promptBuilderService.buildSummaryPrompt(emptyConversation);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("resumir conversas");
        assertThat(prompt).contains("Crie um resumo claro e conciso");
        assertThat(prompt).contains(emptyConversation);
    }
    
    @Test
    void buildPrompt_withLongHistoryAndContext_shouldIncludeAllSections() {
        // Given
        String userMessage = "Pode me dar mais detalhes sobre os serviços?";
        StringBuilder longHistory = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            longHistory.append("[USUARIO]: Mensagem ").append(i).append("\n");
            longHistory.append("[ASSISTENTE]: Resposta ").append(i).append("\n");
        }
        
        ConversationContext context = ConversationContext.builder()
                .customerIntent("SOLICITACAO_INFO")
                .lastDetectedTopic("serviços")
                .identifiedEntities(Arrays.asList("serviços", "detalhes"))
                .conversationState("INFORMACAO_SOLICITADA")
                .lastInteractionTime(LocalDateTime.now())
                .conversationSummary("Cliente solicitando informações sobre serviços oferecidos")
                .needsHumanIntervention(false)
                .gptContext("Conversa sobre detalhes de serviços")
                .build();

        // When
        String prompt = promptBuilderService.buildPrompt(userMessage, longHistory.toString(), context);

        // Then
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains(DEFAULT_SYSTEM_PROMPT);
        assertThat(prompt).contains("### Informações de contexto:");
        assertThat(prompt).contains("- Tópico atual: serviços");
        assertThat(prompt).contains("- Intenção do cliente: SOLICITACAO_INFO");
        assertThat(prompt).contains("- Entidades mencionadas: serviços, detalhes");
        assertThat(prompt).contains("- Estado da conversa: INFORMACAO_SOLICITADA");
        assertThat(prompt).contains("### Histórico da conversa:");
        assertThat(prompt).contains("### Mensagem atual:");
        assertThat(prompt).contains(userMessage);
    }
}