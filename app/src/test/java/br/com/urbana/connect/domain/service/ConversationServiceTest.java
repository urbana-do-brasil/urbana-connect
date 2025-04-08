package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.domain.enums.ConversationStatus;
import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.model.Conversation;
import br.com.urbana.connect.domain.model.ConversationContext;
import br.com.urbana.connect.domain.model.Message;
import br.com.urbana.connect.domain.port.output.ConversationRepository;
import br.com.urbana.connect.domain.port.output.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ConversationService conversationService;

    private Conversation testConversation;
    private Message testMessage;
    private final String CONVERSATION_ID = "test-conversation-id";
    private final String CUSTOMER_ID = "test-customer-id";
    private final String MESSAGE_ID = "test-message-id";

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        
        testConversation = Conversation.builder()
                .id(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.ACTIVE)
                .startTime(now.minusDays(1))
                .lastActivityTime(now.minusHours(1))
                .handedOffToHuman(false)
                .messageIds(new ArrayList<>())
                .context(new ConversationContext())
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusHours(1))
                .build();

        testMessage = Message.builder()
                .id(MESSAGE_ID)
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.INBOUND)
                .content("Teste de mensagem")
                .timestamp(now)
                .build();
    }

    @Test
    void createConversation_whenNoActiveConversation_shouldCreateNewConversation() {
        // Given
        when(conversationRepository.findByCustomerIdAndStatus(CUSTOMER_ID, ConversationStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation savedConversation = invocation.getArgument(0);
            savedConversation.setId(CONVERSATION_ID);
            return savedConversation;
        });

        // When
        Conversation result = conversationService.createConversation(CUSTOMER_ID);

        // Then
        assertNotNull(result);
        assertEquals(CONVERSATION_ID, result.getId());
        assertEquals(CUSTOMER_ID, result.getCustomerId());
        assertEquals(ConversationStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getLastActivityTime());
        assertFalse(result.isHandedOffToHuman());
        
        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversationCaptor.capture());
        Conversation capturedConversation = conversationCaptor.getValue();
        
        assertEquals(CUSTOMER_ID, capturedConversation.getCustomerId());
        assertEquals(ConversationStatus.ACTIVE, capturedConversation.getStatus());
        assertNotNull(capturedConversation.getStartTime());
    }

    @Test
    void createConversation_whenActiveConversationExists_shouldReturnExistingConversation() {
        // Given
        when(conversationRepository.findByCustomerIdAndStatus(CUSTOMER_ID, ConversationStatus.ACTIVE))
                .thenReturn(List.of(testConversation));

        // When
        Conversation result = conversationService.createConversation(CUSTOMER_ID);

        // Then
        assertNotNull(result);
        assertEquals(CONVERSATION_ID, result.getId());
        assertEquals(CUSTOMER_ID, result.getCustomerId());
        
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void findConversation_whenExists_shouldReturnConversation() {
        // Given
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(testConversation));

        // When
        Optional<Conversation> result = conversationService.findConversation(CONVERSATION_ID);

        // Then
        assertTrue(result.isPresent());
        assertEquals(CONVERSATION_ID, result.get().getId());
        assertEquals(CUSTOMER_ID, result.get().getCustomerId());
    }

    @Test
    void findConversation_whenNotExists_shouldReturnEmpty() {
        // Given
        when(conversationRepository.findById(anyString())).thenReturn(Optional.empty());

        // When
        Optional<Conversation> result = conversationService.findConversation("non-existent-id");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void findActiveConversation_whenExists_shouldReturnConversation() {
        // Given
        when(conversationRepository.findByCustomerIdAndStatus(CUSTOMER_ID, ConversationStatus.ACTIVE))
                .thenReturn(List.of(testConversation));

        // When
        Optional<Conversation> result = conversationService.findActiveConversation(CUSTOMER_ID);

        // Then
        assertTrue(result.isPresent());
        assertEquals(CONVERSATION_ID, result.get().getId());
        assertEquals(CUSTOMER_ID, result.get().getCustomerId());
        assertEquals(ConversationStatus.ACTIVE, result.get().getStatus());
    }

    @Test
    void findActiveConversation_whenNoConversations_shouldReturnEmpty() {
        // Given
        when(conversationRepository.findByCustomerIdAndStatus(anyString(), any(ConversationStatus.class)))
                .thenReturn(Collections.emptyList());

        // When
        Optional<Conversation> result = conversationService.findActiveConversation(CUSTOMER_ID);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void addMessageToConversation_shouldUpdateConversationAndSaveMessage() {
        // Given
        Message newMessage = Message.builder()
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.INBOUND)
                .content("Nova mensagem")
                .build();
                
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(testConversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message savedMessage = invocation.getArgument(0);
            savedMessage.setId(MESSAGE_ID);
            return savedMessage;
        });
        
        // When
        Conversation result = conversationService.addMessageToConversation(CONVERSATION_ID, newMessage);

        // Then
        assertNotNull(result);
        assertEquals(CONVERSATION_ID, result.getId());
        
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message capturedMessage = messageCaptor.getValue();
        
        assertEquals(CONVERSATION_ID, capturedMessage.getConversationId());
        assertEquals("Nova mensagem", capturedMessage.getContent());
        assertNotNull(capturedMessage.getTimestamp());
        
        verify(conversationRepository).addMessageId(CONVERSATION_ID, MESSAGE_ID);
    }

    @Test
    void addMessageToConversation_whenConversationNotExists_shouldThrowException() {
        // Given
        Message newMessage = Message.builder()
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.INBOUND)
                .content("Nova mensagem")
                .build();
                
        when(conversationRepository.findById(anyString())).thenReturn(Optional.empty());

        // When, Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> conversationService.addMessageToConversation("non-existent-id", newMessage)
        );
        
        assertEquals("Conversa não encontrada", exception.getMessage());
        verify(messageRepository, never()).save(any(Message.class));
        verify(conversationRepository, never()).addMessageId(anyString(), anyString());
    }

    @Test
    void updateConversationStatus_shouldUpdateStatusAndLastActivityTime() {
        // Given
        Conversation updatedConversation = Conversation.builder()
                .id(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.WAITING_FOR_CUSTOMER)
                .lastActivityTime(LocalDateTime.now())
                .build();
                
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(testConversation));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(updatedConversation);

        // When
        Conversation result = conversationService.updateConversationStatus(CONVERSATION_ID, ConversationStatus.WAITING_FOR_CUSTOMER);

        // Then
        assertNotNull(result);
        assertEquals(CONVERSATION_ID, result.getId());
        assertEquals(ConversationStatus.WAITING_FOR_CUSTOMER, result.getStatus());
        
        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversationCaptor.capture());
        Conversation capturedConversation = conversationCaptor.getValue();
        
        assertEquals(ConversationStatus.WAITING_FOR_CUSTOMER, capturedConversation.getStatus());
        assertNotNull(capturedConversation.getLastActivityTime());
    }

    @Test
    void updateConversationStatus_whenConversationNotExists_shouldThrowException() {
        // Given
        when(conversationRepository.findById(anyString())).thenReturn(Optional.empty());

        // When, Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> conversationService.updateConversationStatus("non-existent-id", ConversationStatus.WAITING_FOR_CUSTOMER)
        );
        
        assertEquals("Conversa não encontrada", exception.getMessage());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void updateConversation_shouldUpdateAllFields() {
        // Given
        Conversation conversationToUpdate = Conversation.builder()
                .id(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.WAITING_HUMAN)
                .handedOffToHuman(true)
                .assignedAgentId("agent-1")
                .context(ConversationContext.builder().customerIntent("AJUDA").build())
                .build();
                
        Conversation expectedResult = Conversation.builder()
                .id(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.WAITING_HUMAN)
                .handedOffToHuman(true)
                .assignedAgentId("agent-1")
                .lastActivityTime(LocalDateTime.now())
                .context(ConversationContext.builder().customerIntent("AJUDA").build())
                .build();
                
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(testConversation));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(expectedResult);

        // When
        Conversation result = conversationService.updateConversation(conversationToUpdate);

        // Then
        assertNotNull(result);
        assertEquals(CONVERSATION_ID, result.getId());
        assertEquals(ConversationStatus.WAITING_HUMAN, result.getStatus());
        assertEquals("agent-1", result.getAssignedAgentId());
        assertTrue(result.isHandedOffToHuman());
        assertEquals("AJUDA", result.getContext().getCustomerIntent());
        
        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversationCaptor.capture());
        Conversation capturedConversation = conversationCaptor.getValue();
        
        assertEquals(ConversationStatus.WAITING_HUMAN, capturedConversation.getStatus());
        assertTrue(capturedConversation.isHandedOffToHuman());
        assertNotNull(capturedConversation.getLastActivityTime());
    }

    @Test
    void updateConversation_withNullId_shouldThrowException() {
        // Given
        Conversation conversationWithNullId = Conversation.builder()
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.ACTIVE)
                .build();

        // When, Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> conversationService.updateConversation(conversationWithNullId)
        );
        
        assertEquals("ID da conversa não pode ser nulo", exception.getMessage());
        verify(conversationRepository, never()).findById(anyString());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void updateConversation_whenConversationNotExists_shouldThrowException() {
        // Given
        Conversation conversationToUpdate = Conversation.builder()
                .id("non-existent-id")
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.ACTIVE)
                .build();
                
        when(conversationRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When, Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> conversationService.updateConversation(conversationToUpdate)
        );
        
        assertEquals("Conversa não encontrada", exception.getMessage());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void closeConversation_shouldUpdateStatusAndSetEndTime() {
        // Given
        Conversation closedConversation = Conversation.builder()
                .id(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.CLOSED)
                .startTime(testConversation.getStartTime())
                .endTime(LocalDateTime.now())
                .build();
                
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(testConversation));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(closedConversation);

        // When
        Conversation result = conversationService.closeConversation(CONVERSATION_ID);

        // Then
        assertNotNull(result);
        assertEquals(CONVERSATION_ID, result.getId());
        assertEquals(ConversationStatus.CLOSED, result.getStatus());
        assertNotNull(result.getEndTime());
        assertEquals(result.getEndTime(), result.getClosedAt());
        
        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversationCaptor.capture());
        Conversation capturedConversation = conversationCaptor.getValue();
        
        assertEquals(ConversationStatus.CLOSED, capturedConversation.getStatus());
        assertNotNull(capturedConversation.getEndTime());
    }

    @Test
    void closeConversation_whenConversationNotExists_shouldThrowException() {
        // Given
        when(conversationRepository.findById(anyString())).thenReturn(Optional.empty());

        // When, Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> conversationService.closeConversation("non-existent-id")
        );
        
        assertEquals("Conversa não encontrada", exception.getMessage());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void listCustomerConversations_shouldReturnConversations() {
        // Given
        List<Conversation> customerConversations = List.of(
            testConversation,
            Conversation.builder()
                .id("conversation-2")
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.CLOSED)
                .build()
        );
        
        when(conversationRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(customerConversations);

        // When
        List<Conversation> result = conversationService.listCustomerConversations(CUSTOMER_ID);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(CONVERSATION_ID, result.get(0).getId());
        assertEquals("conversation-2", result.get(1).getId());
        assertEquals(CUSTOMER_ID, result.get(0).getCustomerId());
        assertEquals(CUSTOMER_ID, result.get(1).getCustomerId());
    }

    @Test
    void getConversationMessages_shouldReturnMessages() {
        // Given
        List<Message> conversationMessages = List.of(
            testMessage,
            Message.builder()
                .id("message-2")
                .conversationId(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .direction(MessageDirection.OUTBOUND)
                .content("Resposta de teste")
                .timestamp(LocalDateTime.now())
                .build()
        );
        
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(testConversation));
        when(messageRepository.findByConversationId(CONVERSATION_ID)).thenReturn(conversationMessages);

        // When
        List<Message> result = conversationService.getConversationMessages(CONVERSATION_ID);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(MESSAGE_ID, result.get(0).getId());
        assertEquals("message-2", result.get(1).getId());
        assertEquals(CONVERSATION_ID, result.get(0).getConversationId());
        assertEquals(CONVERSATION_ID, result.get(1).getConversationId());
    }

    @Test
    void getConversationMessages_whenConversationNotExists_shouldReturnEmptyList() {
        // Given
        when(conversationRepository.findById(anyString())).thenReturn(Optional.empty());

        // When
        List<Message> result = conversationService.getConversationMessages("non-existent-id");

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(messageRepository, never()).findByConversationId(anyString());
    }

    @Test
    void findAll_shouldReturnEmptyList() {
        // Given - The method returns an empty list by default
        
        // When
        List<Conversation> result = conversationService.findAll();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findById_shouldCallFindConversation() {
        // Given
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(testConversation));

        // When
        Optional<Conversation> result = conversationService.findById(CONVERSATION_ID);

        // Then
        assertTrue(result.isPresent());
        assertEquals(CONVERSATION_ID, result.get().getId());
    }

    @Test
    void findByCustomerId_shouldCallListCustomerConversations() {
        // Given
        List<Conversation> customerConversations = List.of(testConversation);
        when(conversationRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(customerConversations);

        // When
        List<Conversation> result = conversationService.findByCustomerId(CUSTOMER_ID);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(CONVERSATION_ID, result.get(0).getId());
    }

    @Test
    void findByStatus_shouldReturnEmptyList() {
        // Given - The method returns an empty list by default
        
        // When
        List<Conversation> result = conversationService.findByStatus(ConversationStatus.ACTIVE);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByCustomerIdAndStatus_shouldCallRepositoryMethod() {
        // Given
        List<Conversation> conversations = List.of(testConversation);
        when(conversationRepository.findByCustomerIdAndStatus(CUSTOMER_ID, ConversationStatus.ACTIVE))
            .thenReturn(conversations);

        // When
        List<Conversation> result = conversationService.findByCustomerIdAndStatus(CUSTOMER_ID, ConversationStatus.ACTIVE);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(CONVERSATION_ID, result.get(0).getId());
        assertEquals(ConversationStatus.ACTIVE, result.get(0).getStatus());
    }

    // Testes para cenários de expiração e retomada de conversas
    
    @Test
    void resumeExpiredConversation_createNewWhenOldConversationIsClosed() {
        // Cenário: Cliente possui uma conversa fechada (expirada) e inicia uma nova conversa
        
        // Given
        when(conversationRepository.findByCustomerIdAndStatus(CUSTOMER_ID, ConversationStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation savedConversation = invocation.getArgument(0);
            savedConversation.setId(CONVERSATION_ID);
            return savedConversation;
        });

        // When
        Conversation result = conversationService.createConversation(CUSTOMER_ID);

        // Then
        assertNotNull(result);
        assertEquals(CONVERSATION_ID, result.getId());
        assertEquals(ConversationStatus.ACTIVE, result.getStatus());
        
        // Verifica que uma nova conversa foi criada
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void conversationStatusTransition_fromActiveToWaitingForCustomer() {
        // Cenário: Conversa muda de ACTIVE para WAITING_FOR_CUSTOMER após envio de mensagem
        
        // Given
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(testConversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Conversation result = conversationService.updateConversationStatus(
                CONVERSATION_ID, ConversationStatus.WAITING_FOR_CUSTOMER);

        // Then
        assertEquals(ConversationStatus.WAITING_FOR_CUSTOMER, result.getStatus());
        
        // Verifica que o status foi atualizado
        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversationCaptor.capture());
        assertEquals(ConversationStatus.WAITING_FOR_CUSTOMER, conversationCaptor.getValue().getStatus());
    }

    @Test
    void conversationStatusTransition_fromWaitingToActive() {
        // Cenário: Conversa muda de WAITING_FOR_CUSTOMER para ACTIVE quando cliente responde
        
        // Given
        Conversation waitingConversation = Conversation.builder()
                .id(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.WAITING_FOR_CUSTOMER)
                .startTime(LocalDateTime.now().minusHours(2))
                .lastActivityTime(LocalDateTime.now().minusHours(1))
                .build();
                
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(waitingConversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Conversation result = conversationService.updateConversationStatus(
                CONVERSATION_ID, ConversationStatus.ACTIVE);

        // Then
        assertEquals(ConversationStatus.ACTIVE, result.getStatus());
        
        // Verifica que o status foi atualizado
        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversationCaptor.capture());
        assertEquals(ConversationStatus.ACTIVE, conversationCaptor.getValue().getStatus());
    }

    @Test
    void handoffConversationToHuman_shouldUpdateStatusAndHandoffFlag() {
        // Cenário: Conversa é transferida para atendimento humano
        
        // Given
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(testConversation));
        
        Conversation updatedConversation = Conversation.builder()
                .id(CONVERSATION_ID)
                .customerId(CUSTOMER_ID)
                .status(ConversationStatus.WAITING_HUMAN)
                .handedOffToHuman(true)
                .assignedAgentId("agent-123")
                .startTime(testConversation.getStartTime())
                .lastActivityTime(LocalDateTime.now())
                .build();
                
        when(conversationRepository.save(any(Conversation.class))).thenReturn(updatedConversation);

        // When - Atualizamos o status e o flag de handoff
        testConversation.setStatus(ConversationStatus.WAITING_HUMAN);
        testConversation.setHandedOffToHuman(true);
        testConversation.setAssignedAgentId("agent-123");
        Conversation result = conversationService.updateConversation(testConversation);

        // Then
        assertEquals(ConversationStatus.WAITING_HUMAN, result.getStatus());
        assertTrue(result.isHandedOffToHuman());
        assertEquals("agent-123", result.getAssignedAgentId());
        
        // Verifica que os campos foram atualizados
        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversationCaptor.capture());
        Conversation captured = conversationCaptor.getValue();
        
        assertEquals(ConversationStatus.WAITING_HUMAN, captured.getStatus());
        assertTrue(captured.isHandedOffToHuman());
        assertEquals("agent-123", captured.getAssignedAgentId());
    }
} 