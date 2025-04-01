package br.com.urbana.connect.domain.port.output;

import java.util.List;

/**
 * Interface para operações de comunicação com a API do GPT.
 * Na arquitetura hexagonal, representa uma porta de saída.
 */
public interface GptServicePort {
    
    /**
     * Gera uma resposta baseada no contexto e na mensagem do usuário.
     * 
     * @param conversationHistory Histórico da conversa formatado
     * @param userMessage Mensagem atual do usuário
     * @param systemPrompt Instruções de sistema para o GPT
     * @return Resposta gerada pelo GPT
     */
    String generateResponse(String conversationHistory, String userMessage, String systemPrompt);
    
    /**
     * Analisa a intenção do usuário a partir de uma mensagem.
     * 
     * @param message Mensagem do usuário
     * @return Intenção detectada
     */
    String analyzeIntent(String message);
    
    /**
     * Verifica se uma mensagem requer intervenção humana.
     * 
     * @param message Mensagem do usuário
     * @param conversationHistory Histórico formatado da conversa
     * @return true se a mensagem requer intervenção humana
     */
    boolean requiresHumanIntervention(String message, String conversationHistory);
    
    /**
     * Extrai entidades de uma mensagem (nomes, produtos, serviços, etc.).
     * 
     * @param message Mensagem a ser analisada
     * @return Lista de entidades extraídas
     */
    List<String> extractEntities(String message);
} 