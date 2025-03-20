package br.com.urbana.connect.domain.port.input;

/**
 * Interface que define os casos de uso para processamento de webhooks.
 * Seguindo o padrão de arquitetura hexagonal, esta é uma porta de entrada.
 */
public interface WebhookUseCase {
    
    /**
     * Processa notificações recebidas do webhook do WhatsApp.
     * 
     * @param payload Payload JSON recebido
     * @return true se o processamento foi bem-sucedido
     */
    boolean processWebhookNotification(String payload);
    
    /**
     * Verifica o token do webhook para validação.
     * 
     * @param token Token de verificação
     * @param challenge Desafio a ser respondido
     * @return O valor do desafio se a verificação for bem-sucedida, ou null
     */
    String verifyWebhook(String token, String challenge);
} 