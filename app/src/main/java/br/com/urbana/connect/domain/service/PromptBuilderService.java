package br.com.urbana.connect.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável pela construção e otimização de prompts para a API da OpenAI.
 * Formata as instruções do sistema, histórico da conversa e mensagem atual.
 */
@Service
@Slf4j
public class PromptBuilderService {
    
    @Value("${openai.system-prompt:Você é um assistente virtual amigável e prestativo da Urbana Conecta.}")
    private String defaultSystemPrompt;
    
    /**
     * Constrói um prompt completo para a API da OpenAI, combinando as instruções do sistema,
     * o histórico da conversa e a mensagem atual do usuário.
     * 
     * @param userMessage Mensagem atual do usuário
     * @param conversationHistory Histórico formatado da conversa
     * @return Prompt completo para envio à API da OpenAI
     */
    public String buildPrompt(String userMessage, String conversationHistory) {
        log.debug("Construindo prompt com histórico de {} caracteres", 
                conversationHistory != null ? conversationHistory.length() : 0);
        
        StringBuilder promptBuilder = new StringBuilder();
        
        // Adicionar instruções do sistema
        promptBuilder.append(getSystemInstructions())
                .append("\n\n");
        
        // Adicionar histórico da conversa se existir
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("### Histórico da conversa:\n")
                    .append(conversationHistory)
                    .append("\n");
        }
        
        // Adicionar mensagem atual do usuário
        promptBuilder.append("User: ")
                .append(userMessage);
        
        String prompt = promptBuilder.toString();
        log.debug("Prompt construído com {} caracteres", prompt.length());
        
        return prompt;
    }
    
    /**
     * Obtém as instruções do sistema (system prompt) para guiar o comportamento do modelo.
     * 
     * @return Instruções do sistema formatadas
     */
    private String getSystemInstructions() {
        return """
                %s
                
                ## Instruções:
                - Responda de forma clara, concisa e amigável.
                - Se você não souber a resposta, diga simplesmente que não tem essa informação.
                - Mantenha suas respostas curtas e diretas.
                - Trate o cliente com respeito e cordialidade.
                - Nunca compartilhe informações sensíveis ou pessoais.
                - Você é especialista em serviços de coleta de lixo, reciclagem e limpeza urbana.
                - Se o cliente solicitar falar com um humano ou demonstrar frustração, informe que pode transferir a conversa para um atendente.
                """.formatted(defaultSystemPrompt);
    }
    
    /**
     * Constrói um prompt para análise de intenção do usuário.
     * Usado para determinar se a mensagem requer intervenção humana.
     * 
     * @param userMessage Mensagem do usuário a ser analisada
     * @return Prompt para análise de intenção
     */
    public String buildIntentAnalysisPrompt(String userMessage) {
        return """
                Analise a seguinte mensagem do usuário e determine sua intenção principal:
                
                "%s"
                
                Responda apenas com UMA das seguintes categorias:
                - DUVIDA_SERVICO: Quando o usuário tem dúvidas sobre serviços oferecidos
                - AGENDAMENTO: Quando o usuário quer agendar ou remarcar um serviço
                - RECLAMACAO: Quando o usuário está insatisfeito ou reclamando
                - CANCELAMENTO: Quando o usuário quer cancelar um serviço
                - CONTATO_HUMANO: Quando o usuário solicita explicitamente falar com um humano
                - OUTRO: Para outras intenções não listadas acima
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
                Avalie a seguinte conversa e determine se é necessário transferir para um atendente humano.
                Responda apenas com SIM ou NÃO.
                
                Critérios para transferir:
                - O usuário pede explicitamente para falar com um humano
                - O usuário demonstra forte frustração ou raiva
                - A pergunta é muito complexa ou específica para ser respondida por um assistente virtual
                - O usuário está em uma situação de emergência
                - Após várias tentativas, o usuário continua sem ter sua dúvida resolvida
                
                """);
        
        // Adicionar histórico se existir
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("### Histórico:\n")
                    .append(conversationHistory)
                    .append("\n\n");
        }
        
        // Adicionar mensagem atual
        promptBuilder.append("### Mensagem atual do usuário:\n")
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
                Extraia as seguintes entidades da mensagem do usuário, se presentes:
                
                Mensagem: "%s"
                
                - Nome: [Nome completo do cliente]
                - Endereço: [Endereço mencionado]
                - Telefone: [Número de telefone mencionado]
                - Email: [Endereço de email mencionado]
                - Data: [Qualquer data mencionada]
                - Serviço: [Tipo de serviço mencionado]
                
                Responda no formato JSON sem incluir entidades que não estão presentes na mensagem.
                """.formatted(userMessage);
    }
} 