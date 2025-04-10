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
            return null;
        }
        
        // Recuperar histórico de mensagens
        List<Message> messageHistory = contextService.getConversationHistory(conversation);
        
        // Verificar se requer intervenção humana
        String formattedHistory = contextService.formatConversationHistory(messageHistory);
        boolean needsHuman = gptService.requiresHumanIntervention(userMessage.getContent(), formattedHistory);
        
        if (needsHuman && !conversation.isHandedOffToHuman()) {
            return createHumanTransferMessage(conversation, userMessage.getCustomerId());
        }
        
        // Gerar resposta com GPT usando o histórico formatado e o contexto
        String responseContent = gptService.generateResponse(
                formattedHistory,
                userMessage.getContent(), 
                SYSTEM_PROMPT);
        
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
    
    private Message createHumanTransferMessage(Conversation conversation, String customerId) {
        Message transferMessage = Message.builder()
                .conversationId(conversation.getId())
                .customerId(customerId)
                .type(MessageType.TEXT)
                .direction(MessageDirection.OUTBOUND)
                .content("Entendi que você precisa falar com um atendente humano. " +
                        "Estou transferindo sua conversa para um de nossos atendentes. " +
                        "Por favor, aguarde um momento.")
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();
        
        Message savedMessage = messageRepository.save(transferMessage);
        conversationService.addMessageToConversation(conversation.getId(), savedMessage);
        
        // Atualizar status da conversa
        conversation.setHandedOffToHuman(true);
        conversation.getContext().setNeedsHumanIntervention(true);
        conversation.setStatus(ConversationStatus.WAITING_FOR_AGENT);
        conversationService.updateConversationStatus(conversation.getId(), ConversationStatus.WAITING_FOR_AGENT);
        
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
            
            // 4. Obter histórico da conversa
            List<Message> conversationHistory = contextService.getConversationHistory(conversation);
            String formattedHistory = contextService.formatConversationHistory(conversationHistory);
            
            // 5. Analisar a intenção do usuário
            String intent = gptService.analyzeIntent(messageContent);
            log.info("Intenção detectada: {}", intent);
            
            // 6. Verificar necessidade de intervenção humana
            boolean needsHuman = gptService.requiresHumanIntervention(messageContent, formattedHistory);
            if (needsHuman) {
                log.info("Mensagem requer intervenção humana");
                conversation.getContext().setNeedsHumanIntervention(true);
                conversationService.updateConversationStatus(conversation.getId(), ConversationStatus.WAITING_HUMAN);
                return "Estou transferindo você para um atendente humano. Por favor, aguarde um momento.";
            }
            
            // 7. Gerar resposta com GPT
            String response = gptService.generateResponse(formattedHistory, messageContent, SYSTEM_PROMPT);
            
            // 8. Extrair entidades e atualizar contexto da conversa
            List<String> entities = gptService.extractEntities(messageContent);
            String entitiesStr = String.join(", ", entities);
            
            // 9. Atualizar o contexto da conversa
            contextService.updateConversationContext(
                    conversation, 
                    intent, 
                    entitiesStr
            );
            
            // 10. Salvar resposta do assistente
            Message assistantMessage = contextService.saveAssistantResponse(conversation, response);
            
            log.info("Resposta gerada com sucesso: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Erro ao processar mensagem: {}", e.getMessage(), e);
            return "Desculpe, ocorreu um erro ao processar sua mensagem. Por favor, tente novamente.";
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