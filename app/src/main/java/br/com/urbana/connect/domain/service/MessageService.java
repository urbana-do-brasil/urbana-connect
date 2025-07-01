package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.application.config.ContextConfig;
import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.enums.ConversationStatus;
import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.enums.MessageStatus;
import br.com.urbana.connect.domain.enums.MessageType;
import br.com.urbana.connect.domain.port.input.ConversationManagementUseCase;
import br.com.urbana.connect.domain.port.input.CustomerManagementUseCase;
import br.com.urbana.connect.domain.port.input.MessageProcessingUseCase;
import br.com.urbana.connect.domain.port.output.GptServicePort;
import br.com.urbana.connect.domain.port.output.MessageRepository;
import br.com.urbana.connect.domain.port.output.WhatsappServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.Arrays;

/**
 * Implementação do caso de uso de processamento de mensagens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService implements MessageProcessingUseCase {
    
    private final MessageRepository messageRepository;
    private final CustomerManagementUseCase customerService;
    private final ConversationManagementUseCase conversationService;
    private final GptServicePort gptService;
    private final WhatsappServicePort whatsappService;
    private final ConversationContextService contextService;
    private final PromptBuilderService promptBuilderService;
    private final ContextConfig contextConfig;
    
    private static final String SYSTEM_PROMPT = "Você é Urba 😉, assistente virtual da Urbana do Brasil, " +
            "uma empresa de Arquitetura e Decoração. Seja amigável, entusiasmada, " +
            "use emojis frequentemente e forneça informações sobre nossos serviços de decoração " +
            "(Decor Interiores 🛋️, Decor Fachada 🏡 e Decor Pintura 🎨), que renovam espaços sem 'quebra-quebra'. " +
            "Se não souber a resposta ou se o cliente solicitar falar com um humano, " +
            "informe que irá transferir para um atendente. 💜";
    
    // Expressões regulares para detecção de saudações
    private static final List<String> GREETING_PATTERNS = List.of(
            "\\boi\\b", "\\bolá\\b", "\\bola\\b", "\\bhello\\b", "\\bhi\\b",
            "\\bbom dia\\b", "\\bboa tarde\\b", "\\bboa noite\\b", "\\bboa\\b",
            "\\btudo bem\\b", "\\bcomo vai\\b", "\\bhey\\b"
    );
    
    @Override
    public Message processInboundMessage(Message inboundMessage) {
        log.debug("Processando mensagem recebida do cliente: {}", inboundMessage.getCustomerId());
        
        // Verificar se o cliente existe ou criar um novo
        Customer customer = contextService.getOrCreateCustomer(inboundMessage.getCustomerId());
        
        // Buscar ou criar conversa ativa
        Conversation conversation = contextService.getOrCreateActiveConversation(customer);
        
        // Salvar mensagem de entrada
        Message savedMessage = contextService.saveUserMessage(
                conversation, 
                inboundMessage.getContent(), 
                inboundMessage.getWhatsappMessageId()
        );
        
        // Marcar como lida no WhatsApp
        if (inboundMessage.getWhatsappMessageId() != null) {
            whatsappService.markMessageAsRead(inboundMessage.getWhatsappMessageId());
        }
        
        // Gerar resposta
        Message response = generateResponse(conversation, savedMessage);
        
        return response;
    }
    
    /**
     * Gera uma resposta para a mensagem do usuário usando o histórico de conversa
     * e análise de contexto.
     * 
     * @param conversation A conversa ativa
     * @param userMessage A mensagem do usuário
     * @return A mensagem de resposta gerada
     */
    public Message generateResponse(Conversation conversation, Message userMessage) {
        log.debug("Gerando resposta para a mensagem: {} na conversa: {}", 
                userMessage.getId(), conversation.getId());
        
        // Verificar se já foi transferido para atendimento humano
        if (conversation.isHandedOffToHuman()) {
            log.info("Conversa já transferida para atendimento humano. Não gerando resposta automática.");
            
            // Enviar mensagem leve de lembrete, se estiver aguardando há muito tempo
            if (conversation.getLastActivityTime() != null &&
                conversation.getLastActivityTime().plusMinutes(2).isBefore(LocalDateTime.now())) {
                return createHandoffReminderMessage(conversation, userMessage.getCustomerId());
            }
            
            return null;
        }
        
        // Recuperar histórico de mensagens
        List<Message> messageHistory = contextService.getConversationHistory(conversation);
        
        // Verificar se requer intervenção humana via detecção com GPT
        String formattedHistory = contextService.formatConversationHistory(messageHistory);
        boolean needsHuman = gptService.requiresHumanIntervention(userMessage.getContent(), formattedHistory);
        
        // Verificar palavras-chave explícitas para handoff
        boolean containsHandoffKeywords = containsHandoffKeywords(userMessage.getContent());
        
        // Se precisar de intervenção humana (por GPT ou palavras-chave)
        if ((needsHuman || containsHandoffKeywords) && !conversation.isHandedOffToHuman()) {
            log.info("Transferindo para atendimento humano. Detectado por: {}", 
                    containsHandoffKeywords ? "palavras-chave" : "análise GPT");
            return createHumanTransferMessage(conversation, userMessage.getCustomerId());
        }
        
        // Verificar se é uma saudação para uma conversa nova ou se é a primeira mensagem
        String responseContent;
        if (isGreeting(userMessage.getContent()) && 
            (messageHistory.size() <= 1 || isFirstMessageInNewSession(conversation, messageHistory))) {
            log.info("Detectada saudação inicial, gerando resposta de boas-vindas");
            
            // Para saudações, utilizamos um prompt específico (sem histórico necessário)
            String greetingPrompt = promptBuilderService.buildGreetingPrompt();
            responseContent = gptService.generateResponse("", "", greetingPrompt);
        } else {
            // Para outras mensagens, usamos o prompt de FAQ que inclui a base de conhecimento
            log.debug("Gerando resposta com base no contexto e possível FAQ");
            String faqPrompt = promptBuilderService.buildFaqPrompt(
                    userMessage.getContent(), 
                    formattedHistory, 
                    conversation.getContext());
                    
            responseContent = gptService.generateResponse(
                    "",  // Histórico já está no prompt
                    userMessage.getContent(), 
                    faqPrompt);
        }
        
        // Salvar resposta
        Message savedResponse = contextService.saveAssistantResponse(
                conversation, 
                responseContent
        );
        
        // Enviar pelo WhatsApp
        String whatsappMessageId = sendResponseViaWhatsapp(savedResponse, userMessage.getCustomerId());
        if (whatsappMessageId != null) {
            savedResponse.setWhatsappMessageId(whatsappMessageId);
            messageRepository.save(savedResponse);
        }
        
        // Atualizar contexto com entidades e intenção detectadas
        updateConversationContext(conversation, userMessage.getContent(), responseContent);
        
        return savedResponse;
    }
    
    /**
     * Verifica se a mensagem é uma saudação comum.
     * 
     * @param message Conteúdo da mensagem a ser verificada
     * @return true se for uma saudação, false caso contrário
     */
    private boolean isGreeting(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        String normalized = message.toLowerCase().trim();
        
        // Verifica se é uma mensagem muito curta (típico de saudações)
        if (normalized.split("\\s+").length <= 3) {
            // Verificar padrões de saudação usando expressões regulares
            for (String pattern : GREETING_PATTERNS) {
                if (normalized.matches(".*" + pattern + ".*")) {
                    log.debug("Saudação detectada com padrão: {}", pattern);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Verifica se é a primeira mensagem em uma nova sessão de conversa.
     * Útil para reapresentar saudações quando o cliente retorna após um período de inatividade.
     * 
     * @param conversation A conversa atual
     * @param messageHistory Histórico de mensagens
     * @return true se for a primeira mensagem de uma nova sessão
     */
    private boolean isFirstMessageInNewSession(Conversation conversation, List<Message> messageHistory) {
        if (messageHistory.size() <= 1) {
            return true;
        }
        
        // Se a última atividade foi há mais de 6 horas, consideramos uma nova sessão
        LocalDateTime lastActivity = conversation.getLastActivityTime();
        return lastActivity != null && 
               lastActivity.plusHours(6).isBefore(LocalDateTime.now());
    }
    
    @Override
    public Message generateResponse(String conversationId, String messageId) {
        // Buscar mensagem original
        Message originalMessage = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada"));
        
        // Buscar conversa
        Conversation conversation = conversationService.findConversation(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        return generateResponse(conversation, originalMessage);
    }
    
    /**
     * Atualiza o contexto da conversa com informações extraídas da mensagem atual.
     * 
     * @param conversation A conversa a ser atualizada
     * @param userMessage A mensagem do usuário
     * @param responseContent A resposta gerada
     */
    private void updateConversationContext(Conversation conversation, String userMessage, String responseContent) {
        try {
            // Analisar intenção
            String intent = gptService.analyzeIntent(userMessage);
            conversation.getContext().setCustomerIntent(intent);
            conversation.getContext().setLastDetectedTopic(intent);
            
            // Extrair entidades
            List<String> entities = gptService.extractEntities(userMessage);
            if (!entities.isEmpty()) {
                conversation.getContext().getIdentifiedEntities().addAll(entities);
            }
            
            // Atualizar timestamp da última interação
            LocalDateTime now = LocalDateTime.now();
            conversation.getContext().setLastInteractionTime(now);
            conversation.setLastActivityTime(now);
            
            // Determinar o estado atual da conversa
            String currentState = determineConversationState(conversation, responseContent);
            conversation.getContext().setConversationState(currentState);
            
            // Se resumo automático estiver habilitado, gerar um resumo da conversa
            if (contextConfig.isSummaryEnabled()) {
                generateConversationSummary(conversation);
            }
            
            // Salvar contexto atualizado - usar o novo método que salva a conversa completa
            conversationService.updateConversation(conversation);
            
            log.debug("Contexto da conversa atualizado. Intenção: {}, Entidades: {}, Estado: {}", 
                    intent, entities, currentState);
        } catch (Exception e) {
            log.error("Erro ao atualizar contexto da conversa: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Determina o estado atual da conversa com base no conteúdo da resposta.
     * 
     * @param conversation A conversa atual
     * @param responseContent O conteúdo da resposta gerada
     * @return O estado atual da conversa
     */
    private String determineConversationState(Conversation conversation, String responseContent) {
        // Se já foi transferido para humano, mantém esse estado
        if (conversation.isHandedOffToHuman()) {
            return "NECESSITA_INTERVENCAO";
        }
        
        // Verifica se a resposta indica que uma intervenção humana é necessária
        if (responseContent.toLowerCase().contains("atendente") ||
            responseContent.toLowerCase().contains("humano") ||
            responseContent.toLowerCase().contains("transferir")) {
            return "POSSIVEL_INTERVENCAO";
        }
        
        // Verifica se parece ser uma despedida
        if (responseContent.toLowerCase().contains("até logo") ||
            responseContent.toLowerCase().contains("adeus") ||
            responseContent.toLowerCase().contains("tchau")) {
            return "FINALIZANDO";
        }
        
        // Estado padrão - aguardando resposta do usuário
        return "AGUARDANDO_USUARIO";
    }
    
    /**
     * Gera um resumo da conversa usando o serviço GPT.
     * Essa é uma funcionalidade avançada que pode ser ativada via configuração.
     * 
     * @param conversation A conversa a ser resumida
     */
    private void generateConversationSummary(Conversation conversation) {
        try {
            // Buscar as últimas mensagens para resumir (limitado a um número menor que o contexto normal)
            List<Message> messages = messageRepository.findByConversationId(conversation.getId());
            if (messages.size() < 4) {
                // Não resumir conversas muito curtas
                return;
            }
            
            // Formatar as mensagens para o resumo
            StringBuilder messageHistory = new StringBuilder();
            for (Message message : messages) {
                String role = message.getDirection() == MessageDirection.INBOUND ? "Usuário" : "Assistente";
                messageHistory.append(role).append(": ").append(message.getContent()).append("\n");
            }
            
            // Usar o PromptBuilderService para construir o prompt de resumo
            String summaryPrompt = promptBuilderService.buildSummaryPrompt(messageHistory.toString());
            
            // Chamar GPT para gerar o resumo
            String summary = gptService.generateResponse("", summaryPrompt, 
                    "Você é um resumidor de conversas. Seja conciso e objetivo.");
            
            // Atualizar o resumo na conversa
            contextService.updateConversationSummary(conversation, summary);
            
            log.debug("Resumo da conversa gerado: {}", summary);
        } catch (Exception e) {
            log.error("Erro ao gerar resumo da conversa: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public boolean transferToHuman(String conversationId, String reason) {
        log.debug("Transferindo conversa para atendimento humano: {}", conversationId);
        
        Conversation conversation = conversationService.findConversation(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada"));
        
        if (conversation.isHandedOffToHuman()) {
            log.info("Conversa já transferida para atendimento humano: {}", conversationId);
            return true;
        }
        
        // Atualizar status da conversa
        conversation.setHandedOffToHuman(true);
        conversation.setStatus(ConversationStatus.WAITING_FOR_AGENT);
        conversationService.updateConversationStatus(conversationId, ConversationStatus.WAITING_FOR_AGENT);
        
        // Adicionar mensagem de notificação
        Customer customer = customerService.findCustomerByPhoneNumber(conversation.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        
        Message transferMessage = Message.builder()
                .conversationId(conversationId)
                .customerId(customer.getId())
                .type(MessageType.TEXT)
                .direction(MessageDirection.OUTBOUND)
                .content("Sua conversa foi transferida para um atendente humano. " +
                        "Em breve alguém entrará em contato. Motivo: " + reason)
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();
        
        Message savedMessage = messageRepository.save(transferMessage);
        conversationService.addMessageToConversation(conversationId, savedMessage);
        
        // Enviar pelo WhatsApp
        String whatsappMessageId = whatsappService.sendTextMessage(
                customer.getPhoneNumber(), transferMessage.getContent());
        
        if (whatsappMessageId != null) {
            savedMessage.setWhatsappMessageId(whatsappMessageId);
            messageRepository.save(savedMessage);
        }
        
        log.info("Conversa transferida com sucesso para atendimento humano: {}", conversationId);
        return true;
    }
    
    @Override
    public boolean processMessageStatusUpdate(String messageId, MessageStatus status) {
        log.debug("Atualizando status da mensagem: {} para {}", messageId, status);
        
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada"));
        
        message.setStatus(status);
        messageRepository.save(message);
        
        log.info("Status da mensagem atualizado com sucesso: {}", messageId);
        return true;
    }
    
    /**
     * Verifica se a mensagem do usuário contém palavras-chave explícitas
     * solicitando atendimento humano.
     * 
     * @param message Conteúdo da mensagem
     * @return true se contém palavras-chave de handoff
     */
    private boolean containsHandoffKeywords(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        String normalized = message.toLowerCase().trim();
        
        // Lista de palavras-chave que indicam pedido explícito de atendimento humano
        List<String> handoffKeywords = Arrays.asList(
            "falar com atendente", "falar com humano", "atendente humano", 
            "quero atendente", "quero falar com pessoa", "falar com pessoa",
            "atendente por favor", "preciso de atendente", "pessoa real",
            "quero falar com alguém", "falar com gente", "atendimento humano",
            "ajuda de verdade", "suporte", "falar com suporte",
            "pessoa de verdade", "sem bot", "não quero falar com robô"
        );
        
        return handoffKeywords.stream().anyMatch(normalized::contains);
    }
    
    /**
     * Cria uma mensagem de transferência para atendimento humano.
     * 
     * @param conversation A conversa a ser transferida
     * @param customerId ID do cliente
     * @return Mensagem criada e salva
     */
    private Message createHumanTransferMessage(Conversation conversation, String customerId) {
        // Criar mensagem de transferência com o estilo "Urba"
        Message transferMessage = Message.builder()
                .conversationId(conversation.getId())
                .customerId(customerId)
                .type(MessageType.TEXT)
                .direction(MessageDirection.OUTBOUND)
                .content("Entendi! 😉 Para te dar a atenção super especial que você merece nesse ponto, " +
                        "vou acionar nossa equipe de especialistas em decoração! 🧑‍🎨 Fica tranquilo(a) que " +
                        "em breve alguém entrará em contato por aqui para continuar a conversa. Até já! ✨💜")
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();
        
        Message savedMessage = messageRepository.save(transferMessage);
        conversationService.addMessageToConversation(conversation.getId(), savedMessage);
        
        // Atualizar status da conversa
        conversation.setHandedOffToHuman(true);
        conversation.getContext().setNeedsHumanIntervention(true);
        conversation.getContext().setConversationState("AGUARDANDO_ATENDENTE");
        conversation.setStatus(ConversationStatus.WAITING_FOR_AGENT);
        conversationService.updateConversation(conversation);
        
        // Enviar pelo WhatsApp
        Customer customer = customerService.findByPhoneNumber(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        
        String whatsappMessageId = whatsappService.sendTextMessage(
                customer.getPhoneNumber(), savedMessage.getContent());
        
        if (whatsappMessageId != null) {
            savedMessage.setWhatsappMessageId(whatsappMessageId);
            messageRepository.save(savedMessage);
        }
        
        return savedMessage;
    }
    
    /**
     * Cria uma mensagem de lembrete para o cliente que está aguardando atendimento humano.
     * 
     * @param conversation A conversa em aguardo
     * @param customerId ID do cliente
     * @return Mensagem de lembrete
     */
    private Message createHandoffReminderMessage(Conversation conversation, String customerId) {
        Message reminderMessage = Message.builder()
                .conversationId(conversation.getId())
                .customerId(customerId)
                .type(MessageType.TEXT)
                .direction(MessageDirection.OUTBOUND)
                .content("Nossa equipe já foi notificada e entrará em contato em breve! 😊 " +
                        "Obrigada pela paciência. 💜")
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();
        
        Message savedMessage = messageRepository.save(reminderMessage);
        
        // Enviar pelo WhatsApp
        Customer customer = customerService.findByPhoneNumber(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        
        String whatsappMessageId = whatsappService.sendTextMessage(
                customer.getPhoneNumber(), savedMessage.getContent());
        
        if (whatsappMessageId != null) {
            savedMessage.setWhatsappMessageId(whatsappMessageId);
            messageRepository.save(savedMessage);
        }
        
        return savedMessage;
    }
    
    private String sendResponseViaWhatsapp(Message response, String customerId) {
        try {
            Customer customer = customerService.findById(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
            
            String messageId = whatsappService.sendTextMessage(
                    customer.getPhoneNumber(), response.getContent());
            
            if (messageId == null) {
                log.error("Falha ao enviar mensagem via WhatsApp para: {}", customer.getPhoneNumber());
            } else {
                log.info("Mensagem enviada com sucesso via WhatsApp. ID: {}", messageId);
            }
            
            return messageId;
        } catch (Exception e) {
            log.error("Erro ao enviar resposta via WhatsApp: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Processa uma mensagem recebida, salvando nos repositórios apropriados
     * e gerando uma resposta utilizando a OpenAI.
     *
     * @param phoneNumber Número de telefone do cliente
     * @param messageContent Conteúdo da mensagem
     * @param whatsappMessageId ID da mensagem no WhatsApp (opcional)
     * @return Resposta gerada para a mensagem
     */
    @Override
    public String processIncomingMessage(String phoneNumber, String messageContent, String whatsappMessageId) {
        log.debug("Processando mensagem de entrada: {}", messageContent);
        MDC.put("messageContent", messageContent);
        
        try {
            // 1. Buscar ou criar cliente e conversa
            Customer customer = contextService.getOrCreateCustomer(phoneNumber);
            Conversation conversation = contextService.getOrCreateActiveConversation(customer);
            
            MDC.put("customerId", customer.getId());
            MDC.put("conversationId", conversation.getId());
            
            // 2. Verifica se a conversa está finalizada
            if (contextService.isEnded(conversation)) {
                log.info("Conversa estava encerrada. Criando uma nova.");
                conversation = contextService.getOrCreateActiveConversation(customer);
            }
            
            // 3. Salvar mensagem do usuário
            Message userMessage = contextService.saveUserMessage(conversation, messageContent, whatsappMessageId);
            
            // 4. Verificar se já está em handoff
            if (conversation.isHandedOffToHuman()) {
                log.info("Conversa já transferida para atendimento humano. Ignorando processamento automático.");
                return "Mensagem recebida. Aguardando atendimento humano.";
            }
            
            // 5. Obter histórico da conversa
            List<Message> conversationHistory = contextService.getConversationHistory(conversation);
            String formattedHistory = contextService.formatConversationHistory(conversationHistory);
            
            // 6. Verificar palavras-chave para handoff
            boolean containsHandoffKeywords = containsHandoffKeywords(messageContent);
            if (containsHandoffKeywords) {
                log.info("Palavras-chave de handoff detectadas. Transferindo para atendimento humano.");
                createHumanTransferMessage(conversation, phoneNumber);
                return "Transferindo para atendente humano...";
            }
            
            // 7. Analisar a intenção do usuário
            String intent = gptService.analyzeIntent(messageContent);
            log.info("Intenção detectada: {}", intent);
            
            // 8. Verificar necessidade de intervenção humana via GPT
            boolean needsHuman = gptService.requiresHumanIntervention(messageContent, formattedHistory);
            if (needsHuman) {
                log.info("Mensagem requer intervenção humana segundo análise do GPT");
                createHumanTransferMessage(conversation, phoneNumber);
                return "Transferindo para atendente humano...";
            }
            
            // 9. Gerar resposta com GPT
            String response = gptService.generateResponse(formattedHistory, messageContent, SYSTEM_PROMPT);
            
            // 10. Extrair entidades e atualizar contexto da conversa
            List<String> entities = gptService.extractEntities(messageContent);
            String entitiesStr = String.join(", ", entities);
            
            // 11. Atualizar o contexto da conversa
            contextService.updateConversationContext(
                    conversation, 
                    intent, 
                    entitiesStr
            );
            
            // 12. Salvar resposta do assistente
            Message assistantMessage = contextService.saveAssistantResponse(conversation, response);
            
            log.info("Resposta gerada com sucesso: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Erro ao processar mensagem: {}", e.getMessage(), e);
            return "Ops! 😅 Parece que tive um probleminha técnico. Poderia tentar me perguntar de novo? Ou, se preferir, diga 'falar com atendente' para chamar nossa equipe.";
        } finally {
            MDC.remove("messageContent");
            MDC.remove("customerId");
            MDC.remove("conversationId");
        }
    }
    
    /**
     * Processa uma notificação de leitura de mensagem do WhatsApp.
     *
     * @param whatsappMessageId ID da mensagem no WhatsApp
     * @return true se processado com sucesso, false caso contrário
     */
    @Override
    public boolean processReadReceipt(String whatsappMessageId) {
        log.debug("Processando confirmação de leitura para mensagem: {}", whatsappMessageId);
        
        try {
            Optional<Message> message = messageRepository.findByWhatsappMessageId(whatsappMessageId);
            
            if (message.isPresent()) {
                Message updatedMessage = message.get();
                updatedMessage.setRead(true);
                updatedMessage.setReadAt(LocalDateTime.now());
                messageRepository.save(updatedMessage);
                log.info("Mensagem marcada como lida: {}", whatsappMessageId);
                return true;
            } else {
                log.warn("Mensagem não encontrada para confirmação de leitura: {}", whatsappMessageId);
                return false;
            }
        } catch (Exception e) {
            log.error("Erro ao processar confirmação de leitura: {}", e.getMessage(), e);
            return false;
        }
    }
} 