package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.domain.model.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serviço responsável pela construção e otimização de prompts para a API da OpenAI.
 * Formata as instruções do sistema, histórico da conversa e mensagem atual, otimizando
 * o uso do contexto para melhorar a qualidade e relevância das respostas.
 */
@Service
@Slf4j
public class PromptBuilderService {
    
    @Value("${openai.system-prompt:Você é um assistente virtual da Urbana do Brasil, especialista em coleta de resíduos e limpeza urbana.}")
    private String defaultSystemPrompt;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withLocale(new Locale("pt", "BR"));
    
    /**
     * Constrói um prompt completo para a API da OpenAI, combinando as instruções do sistema,
     * o histórico da conversa e a mensagem atual do usuário, incorporando informações de contexto.
     * 
     * @param userMessage Mensagem atual do usuário
     * @param conversationHistory Histórico formatado da conversa
     * @param context Objeto de contexto da conversa (opcional)
     * @return Prompt completo para envio à API da OpenAI
     */
    public String buildPrompt(String userMessage, String conversationHistory, ConversationContext context) {
        log.debug("Construindo prompt com histórico de {} caracteres", 
                conversationHistory != null ? conversationHistory.length() : 0);
        
        StringBuilder promptBuilder = new StringBuilder();
        
        // Adicionar instruções do sistema (comportamento e diretrizes)
        promptBuilder.append(getSystemInstructions(context))
                .append("\n\n");
        
        // Adicionar contexto da conversa se disponível
        if (context != null) {
            promptBuilder.append(buildContextSection(context))
                    .append("\n\n");
        }
        
        // Adicionar histórico da conversa se existir
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("### Histórico da conversa:\n")
                    .append(conversationHistory)
                    .append("\n\n");
        }
        
        // Adicionar mensagem atual do usuário
        promptBuilder.append("### Mensagem atual:\n")
                .append(userMessage);
        
        String prompt = promptBuilder.toString();
        log.debug("Prompt construído com {} caracteres", prompt.length());
        
        return prompt;
    }
    
    /**
     * Sobrecarga do método buildPrompt que não requer o contexto da conversa.
     * 
     * @param userMessage Mensagem atual do usuário
     * @param conversationHistory Histórico formatado da conversa
     * @return Prompt completo para envio à API da OpenAI
     */
    public String buildPrompt(String userMessage, String conversationHistory) {
        return buildPrompt(userMessage, conversationHistory, null);
    }
    
    /**
     * Constrói a seção de contexto da conversa com informações relevantes para o modelo.
     * 
     * @param context Objeto de contexto da conversa
     * @return String formatada com informações de contexto
     */
    private String buildContextSection(ConversationContext context) {
        if (context == null) {
            return "";
        }
        
        StringBuilder contextBuilder = new StringBuilder("### Informações de contexto:\n");
        
        // Adicionar tópico detectado se disponível
        if (context.getLastDetectedTopic() != null) {
            contextBuilder.append("- Tópico atual: ").append(context.getLastDetectedTopic()).append("\n");
        }
        
        // Adicionar intenção se disponível
        if (context.getCustomerIntent() != null) {
            contextBuilder.append("- Intenção do cliente: ").append(context.getCustomerIntent()).append("\n");
        }
        
        // Adicionar entidades identificadas
        if (context.getIdentifiedEntities() != null && !context.getIdentifiedEntities().isEmpty()) {
            contextBuilder.append("- Entidades mencionadas: ")
                    .append(String.join(", ", context.getIdentifiedEntities()))
                    .append("\n");
        }
        
        // Adicionar estado da conversa
        if (context.getConversationState() != null) {
            contextBuilder.append("- Estado da conversa: ").append(context.getConversationState()).append("\n");
        }
        
        // Adicionar hora da última interação se disponível
        if (context.getLastInteractionTime() != null) {
            contextBuilder.append("- Última interação em: ")
                    .append(DATE_FORMATTER.format(context.getLastInteractionTime()))
                    .append("\n");
        }
        
        // Adicionar resumo da conversa se disponível
        if (context.getConversationSummary() != null) {
            contextBuilder.append("- Resumo da conversa: ").append(context.getConversationSummary()).append("\n");
        }
        
        return contextBuilder.toString();
    }
    
    /**
     * Obtém as instruções do sistema (system prompt) personalizadas com base no contexto.
     * 
     * @param context Objeto de contexto da conversa (opcional)
     * @return Instruções do sistema formatadas
     */
    private String getSystemInstructions(ConversationContext context) {
        StringBuilder instructionsBuilder = new StringBuilder();
        
        // Personalizar conforme o estado da conversa, se disponível
        String basePrompt = defaultSystemPrompt;
        if (context != null && context.isNeedsHumanIntervention()) {
            basePrompt += " Percebo que esta conversa pode requerer atendimento humano em breve.";
        }
        
        instructionsBuilder.append(basePrompt).append("\n\n");
        
        // Adicionar diretrizes de comportamento
        instructionsBuilder.append("""
                ## Instruções:
                - Você é um assistente da Urbana do Brasil, empresa especializada em coleta de resíduos e limpeza urbana.
                - Use linguagem clara, direta e amigável, com tom cordial e prestativo.
                - Suas respostas devem ser concisas (máximo 3-4 frases) e precisas.
                - Forneça informações específicas sobre serviços de coleta, reciclagem e limpeza.
                - Se o cliente demonstrar frustração ou pedir explicitamente, ofereça transferir para um atendente humano.
                - Se não souber a resposta, seja honesto e diga que não tem essa informação.
                - Nunca invente informações sobre preços, prazos ou serviços que não conhece.
                - Evite linguagem técnica complexa; use termos acessíveis ao público geral.
                - Não solicite ou armazene informações pessoais sensíveis.
                - Mantenha o foco nos serviços da Urbana do Brasil.
                """);
        
        return instructionsBuilder.toString();
    }
    
    /**
     * Constrói um prompt para análise de intenção do usuário.
     * 
     * @param userMessage Mensagem do usuário a ser analisada
     * @return Prompt para análise de intenção
     */
    public String buildIntentAnalysisPrompt(String userMessage) {
        return """
                Analise a seguinte mensagem do usuário e determine sua intenção principal.
                
                ## Mensagem:
                "%s"
                
                ## Instruções:
                - Identifique a principal intenção do usuário na mensagem acima.
                - Responda APENAS com uma das categorias abaixo, sem explicações adicionais:
                
                ## Categorias:
                - DUVIDA_SERVICO: Quando o usuário pergunta sobre serviços oferecidos ou como funcionam
                - AGENDAMENTO: Quando o usuário quer agendar, remarcar ou verificar um serviço
                - RECLAMACAO: Quando o usuário expressa insatisfação ou relata um problema
                - CANCELAMENTO: Quando o usuário quer cancelar um serviço ou contrato
                - CONTATO_HUMANO: Quando o usuário solicita explicitamente falar com um atendente humano
                - PRECO_PAGAMENTO: Quando o usuário pergunta sobre preços, formas de pagamento ou faturas
                - ELOGIO: Quando o usuário expressa satisfação ou agradecimento
                - OUTRO: Para intenções que não se encaixam nas categorias acima
                """.formatted(userMessage);
    }
    
    /**
     * Constrói um prompt para avaliar se a mensagem requer intervenção humana.
     * 
     * @param userMessage Mensagem do usuário
     * @param conversationHistory Histórico da conversa
     * @return Prompt para avaliação de necessidade de intervenção humana
     */
    public String buildHumanInterventionPrompt(String userMessage, String conversationHistory) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("""
                Você é um analisador especializado em determinar quando uma conversa deve ser transferida para atendimento humano.
                                
                ## Instruções:
                - Avalie a conversa e a mensagem atual para determinar se é necessário transferir para um atendente humano.
                - Responda APENAS com "SIM" ou "NÃO" sem explicações adicionais.
                
                ## Critérios para transferir (responder SIM):
                - O usuário pede explicitamente para falar com um humano/atendente/pessoa
                - O usuário demonstra frustração significativa ou irritação (linguagem agressiva, pontuação excessiva)
                - O usuário repete a mesma pergunta após receber resposta (indicando que não ficou satisfeito)
                - O usuário faz perguntas extremamente específicas que exigem conhecimento especializado
                - O usuário menciona emergência ou situação urgente
                - O usuário está reclamando sobre um problema não resolvido
                - O usuário usa linguagem que indica que respostas automáticas não estão ajudando
                
                """);
        
        // Adicionar histórico se existir
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("## Histórico da conversa:\n")
                    .append(conversationHistory)
                    .append("\n\n");
        }
        
        // Adicionar mensagem atual
        promptBuilder.append("## Mensagem atual do usuário:\n")
                .append(userMessage);
        
        return promptBuilder.toString();
    }
    
    /**
     * Constrói um prompt para extração de entidades relevantes da mensagem do usuário.
     * 
     * @param userMessage Mensagem do usuário
     * @return Prompt para extração de entidades
     */
    public String buildEntityExtractionPrompt(String userMessage) {
        return """
                Você é um sistema de extração de entidades especializado em identificar informações relevantes em mensagens.
                
                ## Mensagem:
                "%s"
                
                ## Instruções:
                - Extraia entidades relevantes da mensagem acima.
                - Responda em formato JSON com apenas as entidades presentes na mensagem.
                - Não invente ou adicione informações que não estão explícitas na mensagem.
                - Se uma categoria não estiver presente na mensagem, omita-a completamente do resultado.
                
                ## Categorias a extrair:
                - nome: Nome completo do cliente
                - endereco: Endereço completo ou parcial mencionado
                - bairro: Bairro mencionado
                - cidade: Cidade mencionada
                - telefone: Número de telefone mencionado
                - email: Endereço de email mencionado
                - data: Qualquer data mencionada (agendamento, coleta, etc.)
                - horario: Qualquer horário mencionado
                - servico: Tipo de serviço de coleta ou limpeza mencionado
                - produtos: Produtos ou materiais mencionados
                - valor: Valores monetários, preços ou referências a dinheiro
                - problema: Descrição de problemas ou reclamações específicas
                """.formatted(userMessage);
    }
    
    /**
     * Constrói um prompt para resumir uma conversa.
     * 
     * @param conversationHistory Histórico da conversa a ser resumido
     * @return Prompt para resumo da conversa
     */
    public String buildSummaryPrompt(String conversationHistory) {
        return """
                Você é um especialista em resumir conversas de forma concisa e objetiva.
                
                ## Instruções:
                - Crie um resumo claro e conciso da conversa abaixo em 1-2 frases.
                - Foque nos pontos principais e no tema central da conversa.
                - Identifique a intenção do cliente e quaisquer informações críticas.
                - Mantenha o resumo informativo, mas breve.
                - Não inclua detalhes desnecessários ou redundantes.
                
                ## Conversa para resumir:
                %s
                """.formatted(conversationHistory);
    }
} 