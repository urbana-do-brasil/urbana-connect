package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.application.config.ContextConfig;
import br.com.urbana.connect.domain.enums.ConversationStatus;
import br.com.urbana.connect.domain.enums.CustomerStatus;
import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.enums.MessageStatus;
import br.com.urbana.connect.domain.enums.MessageType;
import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.model.ConversationContext;
import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.port.input.ConversationManagementUseCase;
import br.com.urbana.connect.domain.port.input.CustomerManagementUseCase;
import br.com.urbana.connect.domain.port.output.GptServicePort;
import br.com.urbana.connect.domain.port.output.MessageRepository;
import br.com.urbana.connect.domain.port.output.WhatsappServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.Spy;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private CustomerManagementUseCase customerService;

    @Mock
    private ConversationManagementUseCase conversationService;

    @Mock
    private GptServicePort gptService;

    @Mock
    private WhatsappServicePort whatsappService;

    @Mock
    private ConversationContextService contextService;

    @Mock
    private PromptBuilderService promptBuilderService;

    @Mock
    private ContextConfig contextConfig;

    @Spy
    @InjectMocks
    private MessageService messageService;

    private Message inboundMessage;
    private Message outboundMessage;
    private Message humanTransferMessage;
    private Customer customer;
    private Conversation conversation;
    private Conversation humanHandledConversation;
    private List<Message> messageHistory;
    private List<Message> extendedMessageHistory;

    private final String MESSAGE_ID = "msg-123";
    private final String CONVERSATION_ID = "conv-123";
    private final String CUSTOMER_ID = "cust-123";
    private final String PHONE_NUMBER = "5511999999999";
    private final String WHATSAPP_MESSAGE_ID = "wamid.123";
    private final String MESSAGE_CONTENT = "Olá, preciso de ajuda com a decoração do meu apartamento";
    private final String RESPONSE_CONTENT = "Olá! 💜 Como posso ajudar você com a decoração do seu apartamento hoje? Temos várias opções de serviço que podem te atender! 😊";
    private final String HUMAN_TRANSFER_CONTENT = "Entendi que você precisa falar com um atendente humano! 👋 Vou transferir sua conversa agora mesmo. Aguarde um momentinho, por favor. 💜";
    private final String SUMMARY_CONTENT = "Resumo da conversa entre cliente e assistente sobre serviços de decoração";
    private final String SUMMARY_PROMPT = "Resumir a seguinte conversa: [histórico da conversa]";

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        // Configurar cliente
        customer = Customer.builder()
                .id(CUSTOMER_ID)
                .phoneNumber(PHONE_NUMBER)
                .status(CustomerStatus.ACTIVE)
                .optedIn(true)
                .build();

        // Configurar conversa
        conversation = Conversation.builder()
                .id(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.ACTIVE)
                .startTime(now.minusDays(1))
                .lastActivityTime(now)
                .handedOffToHuman(false)
                .messageIds(new ArrayList<>())
                .context(new ConversationContext())
                .build();
                
        // Configurar conversa já transferida para humano
        humanHandledConversation = Conversation.builder()
                .id("conv-human-123")
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.WAITING_FOR_AGENT)
                .startTime(now.minusDays(1))
                .lastActivityTime(now)
                .handedOffToHuman(true)
                .messageIds(new ArrayList<>())
                .context(new ConversationContext())
                .build();

        // Configurar mensagem de entrada
        inboundMessage = Message.builder()
                .id(MESSAGE_ID)
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.INBOUND)
                .type(MessageType.TEXT)
                .content(MESSAGE_CONTENT)
                .timestamp(now)
                .status(MessageStatus.SENT)
                .whatsappMessageId(WHATSAPP_MESSAGE_ID)
                .build();

        // Configurar mensagem de saída
        outboundMessage = Message.builder()
                .id("resp-123")
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.OUTBOUND)
                .type(MessageType.TEXT)
                .content(RESPONSE_CONTENT)
                .timestamp(now.plusSeconds(30))
                .status(MessageStatus.SENT)
                .whatsappMessageId("wamid.response123")
                .build();

        // Configurar mensagem de transferência para humano
        humanTransferMessage = Message.builder()
                .id("transfer-123")
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.OUTBOUND)
                .type(MessageType.TEXT)
                .content(HUMAN_TRANSFER_CONTENT)
                .timestamp(now.plusSeconds(30))
                .status(MessageStatus.SENT)
                .whatsappMessageId("wamid.transfer123")
                .build();

        // Histórico de mensagens simples
        messageHistory = Arrays.asList(inboundMessage);
        
        // Histórico de mensagens estendido para teste de resumo
        Message message2 = Message.builder()
                .id("msg-124")
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.INBOUND)
                .type(MessageType.TEXT)
                .content("Preciso de informações sobre meu pedido")
                .timestamp(now.plusMinutes(1))
                .status(MessageStatus.SENT)
                .whatsappMessageId("wamid.124")
                .build();
                
        Message response2 = Message.builder()
                .id("resp-124")
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.OUTBOUND)
                .type(MessageType.TEXT)
                .content("Claro, qual o número do seu pedido?")
                .timestamp(now.plusMinutes(1).plusSeconds(30))
                .status(MessageStatus.SENT)
                .whatsappMessageId("wamid.response124")
                .build();
                
        Message message3 = Message.builder()
                .id("msg-125")
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.INBOUND)
                .type(MessageType.TEXT)
                .content("O número é 12345")
                .timestamp(now.plusMinutes(2))
                .status(MessageStatus.SENT)
                .whatsappMessageId("wamid.125")
                .build();
                
        Message response3 = Message.builder()
                .id("resp-125")
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.OUTBOUND)
                .type(MessageType.TEXT)
                .content("Encontrei seu pedido, está em processamento.")
                .timestamp(now.plusMinutes(2).plusSeconds(30))
                .status(MessageStatus.SENT)
                .whatsappMessageId("wamid.response125")
                .build();
                
        extendedMessageHistory = Arrays.asList(
            inboundMessage, outboundMessage, message2, response2, message3, response3
        );
        
        // Configurar mocks
        setupMocks();
    }
    
    private void setupMocks() {
        // Configurar CustomerService
        when(customerService.findById(anyString())).thenReturn(Optional.of(customer));
        when(customerService.findCustomerByPhoneNumber(anyString())).thenReturn(Optional.of(customer));
        when(customerService.findByPhoneNumber(anyString())).thenReturn(Optional.of(customer));
        
        // Configurar ConversationService
        when(conversationService.findConversation(anyString())).thenReturn(Optional.of(conversation));
        when(conversationService.findActiveConversation(anyString())).thenReturn(Optional.of(conversation));
        when(conversationService.updateConversationStatus(anyString(), any(ConversationStatus.class)))
            .thenReturn(conversation);
        
        // Configurar ContextService
        when(contextService.getOrCreateCustomer(anyString())).thenReturn(customer);
        when(contextService.getOrCreateActiveConversation(any(Customer.class))).thenReturn(conversation);
        when(contextService.saveUserMessage(any(), anyString(), anyString())).thenReturn(inboundMessage);
        when(contextService.getConversationHistory(any())).thenReturn(messageHistory);
        when(contextService.formatConversationHistory(any())).thenReturn("Histórico formatado");
        when(contextService.saveAssistantResponse(any(), anyString())).thenReturn(outboundMessage);
        
        // Configurar GptService
        when(gptService.requiresHumanIntervention(anyString(), anyString())).thenReturn(false);
        when(gptService.generateResponse(anyString(), anyString(), anyString())).thenReturn(RESPONSE_CONTENT);
        when(gptService.analyzeIntent(anyString())).thenReturn("INTENT_HELP");
        
        // Configurar MessageRepository
        when(messageRepository.findById(anyString())).thenReturn(Optional.of(inboundMessage));
        when(messageRepository.findByWhatsappMessageId(anyString())).thenReturn(Optional.of(inboundMessage));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            if (msg.getContent() != null && msg.getContent().contains("atendente humano")) {
                return humanTransferMessage;
            }
            return msg;
        });
        
        // Configurar WhatsappService
        when(whatsappService.sendMessage(anyString(), any(Message.class))).thenReturn("wamid.response123");
        when(whatsappService.markMessageAsRead(anyString())).thenReturn(true);
        when(whatsappService.sendTextMessage(anyString(), anyString())).thenReturn("wamid.transfer123");
    }

    @Test
    void processInboundMessage_shouldProcessMessageAndGenerateResponse() {
        // Configurar mock de promptBuilderService para retornar um prompt FAQ
        when(promptBuilderService.buildFaqPrompt(anyString(), anyString(), any())).thenReturn("Prompt FAQ");
        
        // Configurar mock para que o GPT não detecte necessidade de intervenção humana
        when(gptService.requiresHumanIntervention(anyString(), anyString())).thenReturn(false);
        
        // When
        Message result = messageService.processInboundMessage(inboundMessage);

        // Then
        assertNotNull(result);
        verify(whatsappService).markMessageAsRead(WHATSAPP_MESSAGE_ID);
        verify(contextService).saveUserMessage(any(), anyString(), anyString());
        verify(contextService).getConversationHistory(any());
        verify(gptService).generateResponse(anyString(), anyString(), anyString());
    }

    @Test
    void processIncomingMessage_shouldProcessMessageByPhoneNumber() {
        // When
        String result = messageService.processIncomingMessage(PHONE_NUMBER, MESSAGE_CONTENT, WHATSAPP_MESSAGE_ID);

        // Then
        assertNotNull(result);
        verify(contextService).getOrCreateCustomer(PHONE_NUMBER);
        verify(contextService).getOrCreateActiveConversation(customer);
    }
    
    @Test
    void generateResponse_withExistingConversationAndMessage_shouldGenerateResponse() {
        // Configurar mock de promptBuilderService para retornar um prompt FAQ
        when(promptBuilderService.buildFaqPrompt(anyString(), anyString(), any())).thenReturn("Prompt FAQ");
        
        // Reconfigurar o mock da mensagem para ter conteúdo não-saudação
        Message testMessage = Message.builder()
                .id(MESSAGE_ID)
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.INBOUND)
                .type(MessageType.TEXT)
                .content("Preciso de informações sobre o serviço de decoração")
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .whatsappMessageId(WHATSAPP_MESSAGE_ID)
                .build();
        
        // Ajustar o repositório para retornar nossa mensagem específica
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(testMessage));
        
        // When
        Message result = messageService.generateResponse(CONVERSATION_ID, MESSAGE_ID);

        // Then
        assertNotNull(result);
        verify(messageRepository).findById(MESSAGE_ID);
        verify(conversationService).findConversation(CONVERSATION_ID);
        verify(gptService).generateResponse(anyString(), anyString(), anyString());
    }
    
    @Test
    void processMessageStatusUpdate_shouldUpdateMessageStatus() {
        // When
        boolean result = messageService.processMessageStatusUpdate(MESSAGE_ID, MessageStatus.DELIVERED);

        // Then
        assertTrue(result);
        verify(messageRepository).findById(MESSAGE_ID);
        verify(messageRepository).save(any(Message.class));
    }
    
    @Test
    void processReadReceipt_shouldUpdateMessageAsRead() {
        // When
        boolean result = messageService.processReadReceipt(WHATSAPP_MESSAGE_ID);

        // Then
        assertTrue(result);
        verify(messageRepository).findByWhatsappMessageId(WHATSAPP_MESSAGE_ID);
        verify(messageRepository).save(any(Message.class));
    }
    
    @Test
    void generateResponse_withHumanInterventionRequired_shouldTransferToHuman() {
        // Given
        when(gptService.requiresHumanIntervention(anyString(), anyString())).thenReturn(true);
        
        // Configurar comportamento para simular a criação da mensagem de transferência
        when(messageRepository.save(argThat(message -> 
            message.getDirection() == MessageDirection.OUTBOUND && 
            message.getContent() != null && 
            message.getContent().contains("atendente humano")
        ))).thenReturn(humanTransferMessage);

        // When
        Message result = messageService.generateResponse(conversation, inboundMessage);

        // Then
        assertNotNull(result);
        assertEquals(humanTransferMessage, result);
        verify(gptService).requiresHumanIntervention(inboundMessage.getContent(), "Histórico formatado");
        
        // Verificar que a conversa foi atualizada para indicar intervenção humana
        verify(conversationService).updateConversationStatus(eq(CONVERSATION_ID), eq(ConversationStatus.WAITING_FOR_AGENT));
        
        // Verificar que não foi chamado o método de geração de resposta do GPT
        verify(gptService, never()).generateResponse(anyString(), anyString(), anyString());
    }
    
    @Test
    void generateResponse_withConversationAlreadyHandedOffToHuman_shouldReturnNull() {
        // When
        Message result = messageService.generateResponse(humanHandledConversation, inboundMessage);
        
        // Then
        assertNull(result);
        
        // Verificar que não foram chamados os métodos de processamento
        verify(contextService, never()).getConversationHistory(any());
        verify(gptService, never()).requiresHumanIntervention(anyString(), anyString());
        verify(gptService, never()).generateResponse(anyString(), anyString(), anyString());
        verify(contextService, never()).saveAssistantResponse(any(), anyString());
    }
    
    @Test
    void determineConversationState_withDifferentResponseContents_shouldReturnCorrectStates() throws Exception {
        // Obter acesso ao método privado
        Method determineConversationStateMethod = MessageService.class.getDeclaredMethod(
            "determineConversationState", Conversation.class, String.class);
        determineConversationStateMethod.setAccessible(true);
        
        // Testar para conversa já transferida para humano
        String state1 = (String) determineConversationStateMethod.invoke(
            messageService, humanHandledConversation, "Qualquer conteúdo");
        assertEquals("NECESSITA_INTERVENCAO", state1);
        
        // Testar para resposta com menção a atendente
        String state2 = (String) determineConversationStateMethod.invoke(
            messageService, conversation, "Por favor, aguarde enquanto um atendente analisa seu projeto. 💜");
        assertEquals("POSSIVEL_INTERVENCAO", state2);
        
        // Testar para resposta com menção a humano
        String state3 = (String) determineConversationStateMethod.invoke(
            messageService, conversation, "Um humano vai te atender em breve! 👋");
        assertEquals("POSSIVEL_INTERVENCAO", state3);
        
        // Testar para resposta com menção a transferir
        String state4 = (String) determineConversationStateMethod.invoke(
            messageService, conversation, "Vou transferir você para um especialista em decoração. ✨");
        assertEquals("POSSIVEL_INTERVENCAO", state4);
        
        // Testar para despedida "até logo"
        String state5 = (String) determineConversationStateMethod.invoke(
            messageService, conversation, "Até logo! Tenha um bom dia. 💜");
        assertEquals("FINALIZANDO", state5);
        
        // Testar para despedida "adeus"
        String state6 = (String) determineConversationStateMethod.invoke(
            messageService, conversation, "Adeus e obrigada por escolher a Urbana! ✌️");
        assertEquals("FINALIZANDO", state6);
    }
    
    @Test
    void generateConversationSummary_withValidConversation_shouldGenerateSummary() throws Exception {
        // Configurar mock para retornar histórico de mensagens estendido
        when(messageRepository.findByConversationId(CONVERSATION_ID)).thenReturn(extendedMessageHistory);
        
        // Configurar mock do promptBuilderService
        when(promptBuilderService.buildSummaryPrompt(anyString())).thenReturn(SUMMARY_PROMPT);
        
        // Configurar mock do gptService para gerar um resumo
        when(gptService.generateResponse(eq(""), eq(SUMMARY_PROMPT), anyString())).thenReturn(SUMMARY_CONTENT);
        
        // Obter acesso ao método privado
        Method generateConversationSummaryMethod = MessageService.class.getDeclaredMethod(
            "generateConversationSummary", Conversation.class);
        generateConversationSummaryMethod.setAccessible(true);
        
        // Quando
        generateConversationSummaryMethod.invoke(messageService, conversation);
        
        // Então
        verify(messageRepository).findByConversationId(CONVERSATION_ID);
        verify(promptBuilderService).buildSummaryPrompt(anyString());
        verify(gptService).generateResponse(eq(""), eq(SUMMARY_PROMPT), anyString());
        verify(contextService).updateConversationSummary(eq(conversation), eq(SUMMARY_CONTENT));
    }
    
    @Test
    void generateConversationSummary_withShortConversation_shouldNotGenerateSummary() throws Exception {
        // Configurar mock para retornar histórico curto de mensagens (menos de 4)
        List<Message> shortHistory = Arrays.asList(inboundMessage, outboundMessage);
        when(messageRepository.findByConversationId(CONVERSATION_ID)).thenReturn(shortHistory);
        
        // Obter acesso ao método privado
        Method generateConversationSummaryMethod = MessageService.class.getDeclaredMethod(
            "generateConversationSummary", Conversation.class);
        generateConversationSummaryMethod.setAccessible(true);
        
        // Quando
        generateConversationSummaryMethod.invoke(messageService, conversation);
        
        // Então
        verify(messageRepository).findByConversationId(CONVERSATION_ID);
        verify(promptBuilderService, never()).buildSummaryPrompt(anyString());
        verify(gptService, never()).generateResponse(anyString(), anyString(), anyString());
        verify(contextService, never()).updateConversationSummary(any(), anyString());
    }
    
    @Test
    void generateConversationSummary_withException_shouldHandleGracefully() throws Exception {
        // Configurar mock para retornar histórico de mensagens estendido
        when(messageRepository.findByConversationId(CONVERSATION_ID)).thenReturn(extendedMessageHistory);
        
        // Configurar mock do promptBuilderService
        when(promptBuilderService.buildSummaryPrompt(anyString())).thenReturn(SUMMARY_PROMPT);
        
        // Configurar mock do gptService para lançar exceção
        when(gptService.generateResponse(eq(""), eq(SUMMARY_PROMPT), anyString()))
            .thenThrow(new RuntimeException("Erro ao gerar resumo"));
        
        // Obter acesso ao método privado
        Method generateConversationSummaryMethod = MessageService.class.getDeclaredMethod(
            "generateConversationSummary", Conversation.class);
        generateConversationSummaryMethod.setAccessible(true);
        
        // Quando
        generateConversationSummaryMethod.invoke(messageService, conversation);
        
        // Então - não deve lançar exceção, deve tratar internamente
        verify(messageRepository).findByConversationId(CONVERSATION_ID);
        verify(promptBuilderService).buildSummaryPrompt(anyString());
        verify(gptService).generateResponse(eq(""), eq(SUMMARY_PROMPT), anyString());
        // Não deve atualizar contexto devido ao erro
        verify(contextService, never()).updateConversationSummary(any(), anyString());
    }
    
    @Test
    void sendResponseViaWhatsapp_withNullMessageId_shouldHandleGracefully() throws Exception {
        // Preparar
        when(whatsappService.sendTextMessage(anyString(), anyString())).thenReturn(null);
        
        // Obter acesso ao método privado
        Method sendResponseViaWhatsappMethod = MessageService.class.getDeclaredMethod(
            "sendResponseViaWhatsapp", Message.class, String.class);
        sendResponseViaWhatsappMethod.setAccessible(true);
        
        // Quando
        String result = (String) sendResponseViaWhatsappMethod.invoke(messageService, outboundMessage, CUSTOMER_ID);
        
        // Então
        assertNull(result);
        verify(whatsappService).sendTextMessage(eq(PHONE_NUMBER), anyString());
        // Verificar que o log de erro seria chamado (não possível em teste unitário)
    }
    
    @Test
    void sendResponseViaWhatsapp_withException_shouldHandleGracefully() throws Exception {
        // Preparar
        when(whatsappService.sendTextMessage(anyString(), anyString()))
            .thenThrow(new RuntimeException("Erro ao enviar mensagem"));
        
        // Obter acesso ao método privado
        Method sendResponseViaWhatsappMethod = MessageService.class.getDeclaredMethod(
            "sendResponseViaWhatsapp", Message.class, String.class);
        sendResponseViaWhatsappMethod.setAccessible(true);
        
        // Quando
        String result = (String) sendResponseViaWhatsappMethod.invoke(messageService, outboundMessage, CUSTOMER_ID);
        
        // Então
        assertNull(result);
        verify(whatsappService).sendTextMessage(eq(PHONE_NUMBER), anyString());
    }
    
    @Test
    void updateConversationContext_withException_shouldHandleGracefully() throws Exception {
        // Preparar - configurar exceção no GptService
        doThrow(new RuntimeException("Erro ao analisar intenção")).when(gptService).analyzeIntent(anyString());
        
        // Obter acesso ao método privado
        Method updateConversationContextMethod = MessageService.class.getDeclaredMethod(
            "updateConversationContext", Conversation.class, String.class, String.class);
        updateConversationContextMethod.setAccessible(true);
        
        // Quando
        updateConversationContextMethod.invoke(messageService, conversation, MESSAGE_CONTENT, RESPONSE_CONTENT);
        
        // Então - o teste passa se não houver exceção lançada
        // Não fazemos verificações adicionais, apenas confirmamos que o método trata exceções graciosamente
    }
    
    @Test
    void processIncomingMessage_withWhatsappError_shouldHandleGracefully() {
        // Preparar
        when(whatsappService.markMessageAsRead(anyString())).thenThrow(new RuntimeException("Erro WhatsApp"));
        
        // Quando
        String result = messageService.processIncomingMessage(PHONE_NUMBER, MESSAGE_CONTENT, WHATSAPP_MESSAGE_ID);
        
        // Então
        assertNotNull(result);
        // Verificar que o serviço ainda processa a mensagem mesmo com erro no WhatsApp
        verify(contextService).getOrCreateCustomer(PHONE_NUMBER);
        verify(contextService).getOrCreateActiveConversation(any(Customer.class));
    }
    
    @Test
    void generateResponse_withRepositoryException_shouldHandleGracefully() {
        // Preparar - sobrescrever o mock do repositório para lançar exceção
        MessageRepository mockRepo = mock(MessageRepository.class);
        when(mockRepo.findById(anyString())).thenThrow(new RuntimeException("Erro no repositório"));
        
        // Criar um novo serviço com o mock modificado
        MessageService testService = new MessageService(
            mockRepo,
            customerService,
            conversationService,
            gptService,
            whatsappService,
            contextService,
            promptBuilderService,
            contextConfig
        );
        
        // Quando - executa o método que deve tratar a exceção internamente
        try {
            testService.generateResponse(CONVERSATION_ID, MESSAGE_ID);
            fail("Deveria lançar uma exceção");
        } catch (Exception e) {
            // Sucesso - exceção esperada
            assertTrue(e instanceof IllegalArgumentException || e instanceof RuntimeException);
        }
    }
    
    @Test
    void processMessageStatusUpdate_withMessageNotFound_shouldHandleException() {
        // Preparar - sobrescrever o mock do repositório para retornar vazio
        when(messageRepository.findById(eq(MESSAGE_ID))).thenReturn(Optional.empty());
        
        // Quando & Então - verificar que a chamada resulta em exceção
        try {
            messageService.processMessageStatusUpdate(MESSAGE_ID, MessageStatus.DELIVERED);
            fail("Deveria lançar uma exceção");
        } catch (Exception e) {
            // Sucesso - exceção esperada
            assertTrue(e instanceof IllegalArgumentException);
            assertEquals("Mensagem não encontrada", e.getMessage());
        }
    }
    
    @Test
    void processReadReceipt_withMessageNotFound_shouldReturnFalse() {
        // Reconfigurar o mock para retornar Optional vazio quando chamado com o ID específico de mensagem
        when(messageRepository.findByWhatsappMessageId(eq(WHATSAPP_MESSAGE_ID))).thenReturn(Optional.empty());
        
        // Quando
        boolean result = messageService.processReadReceipt(WHATSAPP_MESSAGE_ID);
        
        // Então
        assertFalse(result);
        verify(messageRepository).findByWhatsappMessageId(WHATSAPP_MESSAGE_ID);
        verify(messageRepository, never()).save(any(Message.class));
    }
    
    @Test
    void transferToHuman_withActiveConversation_shouldTransferSuccessfully() {
        // When
        boolean result = messageService.transferToHuman(CONVERSATION_ID, "Cliente solicitou atendimento humano");

        // Then
        assertTrue(result);
        verify(conversationService).findConversation(CONVERSATION_ID);
        verify(conversationService).updateConversationStatus(eq(CONVERSATION_ID), eq(ConversationStatus.WAITING_FOR_AGENT));
        verify(customerService).findCustomerByPhoneNumber(anyString());
        verify(whatsappService).sendTextMessage(anyString(), anyString());
        verify(messageRepository, times(2)).save(any(Message.class)); // Salva a mensagem e depois atualiza com whatsapp id
    }
    
    @Test
    void transferToHuman_withAlreadyTransferredConversation_shouldReturnTrue() {
        // Given
        when(conversationService.findConversation(anyString())).thenReturn(Optional.of(humanHandledConversation));
        
        // When
        boolean result = messageService.transferToHuman("conv-human-123", "Transferência desnecessária");
        
        // Then
        assertTrue(result);
        verify(conversationService).findConversation("conv-human-123");
        
        // Não deve chamar os métodos de transferência novamente
        verify(conversationService, never()).updateConversationStatus(anyString(), any(ConversationStatus.class));
        verify(customerService, never()).findCustomerByPhoneNumber(anyString());
        verify(whatsappService, never()).sendTextMessage(anyString(), anyString());
    }
    
    @Test
    void transferToHuman_withConversationNotFound_shouldThrowException() {
        // Given
        when(conversationService.findConversation(eq("conversa-inexistente"))).thenReturn(Optional.empty());
        
        // When & Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            messageService.transferToHuman("conversa-inexistente", "Conversa não existe");
        });
        
        assertEquals("Conversa não encontrada", exception.getMessage());
    }
    
    @Test
    void transferToHuman_withCustomerNotFound_shouldThrowException() {
        // Given
        when(customerService.findCustomerByPhoneNumber(anyString())).thenReturn(Optional.empty());
        
        // When & Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            messageService.transferToHuman(CONVERSATION_ID, "Cliente não encontrado");
        });
        
        assertEquals("Cliente não encontrado", exception.getMessage());
    }
    
    @Test
    void transferToHuman_withWhatsappError_shouldStillCompleteTransfer() {
        // Given
        when(whatsappService.sendTextMessage(anyString(), anyString())).thenReturn(null);
        
        // When
        boolean result = messageService.transferToHuman(CONVERSATION_ID, "Erro no WhatsApp");
        
        // Then
        assertTrue(result);
        verify(conversationService).findConversation(CONVERSATION_ID);
        verify(conversationService).updateConversationStatus(eq(CONVERSATION_ID), eq(ConversationStatus.WAITING_FOR_AGENT));
        verify(customerService).findCustomerByPhoneNumber(anyString());
        verify(whatsappService).sendTextMessage(anyString(), anyString());
        
        // A mensagem ainda é salva, mas não é atualizada com o ID do WhatsApp
        verify(messageRepository).save(any(Message.class));
    }
} 