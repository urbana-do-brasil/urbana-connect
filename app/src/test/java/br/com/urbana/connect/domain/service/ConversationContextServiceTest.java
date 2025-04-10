package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.application.config.ContextConfig;
import br.com.urbana.connect.domain.enums.ConversationStatus;
import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.model.ConversationContext;
import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.infrastructure.persistence.ConversationMongoRepository;
import br.com.urbana.connect.infrastructure.persistence.CustomerMongoRepository;
import br.com.urbana.connect.infrastructure.persistence.MessageMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationContextServiceTest {

    @Mock
    private CustomerMongoRepository customerRepository;

    @Mock
    private ConversationMongoRepository conversationRepository;

    @Mock
    private MessageMongoRepository messageRepository;

    @Mock
    private ContextConfig contextConfig;

    @InjectMocks
    private ConversationContextService contextService;

    private Customer testCustomer;
    private Conversation testConversation;
    private List<Message> testMessages;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        
        // Configurar cliente de teste
        testCustomer = Customer.builder()
                .id("customer123")
                .phoneNumber("+5511999999999")
                .createdAt(now)
                .build();
        
        // Configurar conversa de teste
        testConversation = Conversation.builder()
                .id("conversation456")
                .customerId(testCustomer.getId())
                .status(ConversationStatus.ACTIVE)
                .startTime(now)
                .lastActivityTime(now)
                .context(new ConversationContext())
                .build();
        
        // Configurar mensagens de teste
        testMessages = new ArrayList<>();
        
        // Mensagem 1: Usuário -> Sistema
        testMessages.add(Message.builder()
                .id("msg1")
                .conversationId(testConversation.getId())
                .customerId(testCustomer.getId())
                .content("Olá, gostaria de informações sobre os serviços de decoração.")
                .direction(MessageDirection.INBOUND)
                .timestamp(now.minusMinutes(10))
                .build());
        
        // Mensagem 2: Sistema -> Usuário
        testMessages.add(Message.builder()
                .id("msg2")
                .conversationId(testConversation.getId())
                .customerId(testCustomer.getId())
                .content("Olá! 💜 Temos serviços de Decor Interiores 🛋️, Decor Fachada 🏡 e Decor Pintura 🎨. Como posso ajudar? 😉")
                .direction(MessageDirection.OUTBOUND)
                .timestamp(now.minusMinutes(9))
                .build());
        
        // Mensagem 3: Usuário -> Sistema
        testMessages.add(Message.builder()
                .id("msg3")
                .conversationId(testConversation.getId())
                .customerId(testCustomer.getId())
                .content("Quanto custa o serviço de decoração para um ambiente pequeno?")
                .direction(MessageDirection.INBOUND)
                .timestamp(now.minusMinutes(8))
                .build());
        
        // Mensagem 4: Sistema -> Usuário
        testMessages.add(Message.builder()
                .id("msg4")
                .conversationId(testConversation.getId())
                .customerId(testCustomer.getId())
                .content("Para ambientes pequenos (até 20m²), o nosso serviço Decor custa R$350 por ambiente! 🎉 Você recebe um projeto completo e pode fazer você mesmo, seguindo nossos tutoriais. 🤩")
                .direction(MessageDirection.OUTBOUND)
                .timestamp(now.minusMinutes(7))
                .build());
    }

    @Test
    void getOrCreateCustomer_existingCustomer_returnsCustomer() {
        // Given
        when(customerRepository.findByPhoneNumber(anyString())).thenReturn(Optional.of(testCustomer));
        
        // When
        Customer result = contextService.getOrCreateCustomer(testCustomer.getPhoneNumber());
        
        // Then
        assertNotNull(result);
        assertEquals(testCustomer.getId(), result.getId());
        verify(customerRepository, times(1)).findByPhoneNumber(testCustomer.getPhoneNumber());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void getOrCreateCustomer_newCustomer_createsAndReturnsCustomer() {
        // Given
        when(customerRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        Customer result = contextService.getOrCreateCustomer("+5511888888888");
        
        // Then
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("+5511888888888", result.getPhoneNumber());
        verify(customerRepository, times(1)).findByPhoneNumber("+5511888888888");
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    void getConversationHistory_returnsMessages() {
        // Given
        when(messageRepository.findByConversationIdOrderByTimestampAsc(testConversation.getId()))
                .thenReturn(testMessages);
        when(contextConfig.getMaxMessages()).thenReturn(10); // Configurar para retornar todas as mensagens
        
        // When
        List<Message> result = contextService.getConversationHistory(testConversation);
        
        // Then
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals(testMessages, result);
    }

    @Test
    void getConversationHistory_withLimit_returnsLimitedMessages() {
        // Given
        when(messageRepository.findByConversationIdOrderByTimestampAsc(testConversation.getId()))
                .thenReturn(testMessages);
        when(contextConfig.getMaxMessages()).thenReturn(2); // Configurar para limitar a 2 mensagens
        
        // When
        List<Message> result = contextService.getConversationHistory(testConversation);
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        // Deve retornar as duas últimas mensagens
        assertEquals(testMessages.get(2).getId(), result.get(0).getId());
        assertEquals(testMessages.get(3).getId(), result.get(1).getId());
    }

    @Test
    void formatConversationHistory_formatsMessagesCorrectly() {
        // Given
        when(contextConfig.getTokenLimit()).thenReturn(1000); // Limite alto para não truncar
        
        // When
        String result = contextService.formatConversationHistory(testMessages);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("[USUARIO]: Olá, gostaria de informações sobre os serviços de decoração."));
        assertTrue(result.contains("[ASSISTENTE]: Olá! 💜 Temos serviços de Decor Interiores"));
        assertTrue(result.contains("[USUARIO]: Quanto custa o serviço de decoração para um ambiente pequeno?"));
        assertTrue(result.contains("[ASSISTENTE]: Para ambientes pequenos (até 20m²), o nosso serviço Decor custa R$350"));
    }

    @Test
    void formatConversationHistory_withTokenLimit_truncatesHistory() {
        // Given
        // Configurar para truncar aproximadamente no meio da história
        when(contextConfig.getTokenLimit()).thenReturn(50);
        
        // When
        String result = contextService.formatConversationHistory(testMessages);
        
        // Then
        assertNotNull(result);
        // Verificar que contém apenas as primeiras mensagens
        assertTrue(result.contains("[USUARIO]: Olá, gostaria de informações sobre os serviços de decoração."));
        // Não deve conter a última mensagem
        assertFalse(result.contains("Para ambientes pequenos (até 20m²), o nosso serviço Decor custa R$350"));
    }

    @Test
    void updateConversationContext_updatesContextCorrectly() {
        // Given
        String detectedTopic = "DUVIDA_SERVICO";
        String identifiedEntities = "decoração, interiores, custo";
        when(conversationRepository.save(any(Conversation.class))).thenReturn(testConversation);
        
        // When
        Conversation result = contextService.updateConversationContext(testConversation, detectedTopic, identifiedEntities);
        
        // Then
        assertNotNull(result);
        assertEquals(detectedTopic, result.getContext().getLastDetectedTopic());
        assertTrue(result.getContext().getIdentifiedEntities().contains(identifiedEntities));
        assertNotNull(result.getContext().getLastInteractionTime());
        verify(conversationRepository, times(1)).save(testConversation);
    }

    @Test
    void updateConversationSummary_whenSummaryEnabled_updatesSummary() {
        // Given
        String summary = "Cliente perguntou sobre serviços de coleta e preços para residências pequenas.";
        when(contextConfig.isSummaryEnabled()).thenReturn(true);
        when(conversationRepository.save(any(Conversation.class))).thenReturn(testConversation);
        
        // When
        Conversation result = contextService.updateConversationSummary(testConversation, summary);
        
        // Then
        assertNotNull(result);
        assertEquals(summary, result.getContext().getConversationSummary());
        verify(conversationRepository, times(1)).save(testConversation);
    }

    @Test
    void updateConversationSummary_whenSummaryDisabled_doesNotUpdateSummary() {
        // Given
        String summary = "Cliente perguntou sobre serviços de coleta e preços para residências pequenas.";
        when(contextConfig.isSummaryEnabled()).thenReturn(false);
        
        // When
        Conversation result = contextService.updateConversationSummary(testConversation, summary);
        
        // Then
        assertNotNull(result);
        assertNull(result.getContext().getConversationSummary());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }
} 