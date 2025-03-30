package br.com.urbana.connect.domain.model;

import br.com.urbana.connect.domain.enums.ConversationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTest {

    @Test
    void shouldCreateConversationSuccessfully() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        String customerId = "cust123";
        List<String> messageIds = new ArrayList<>();
        messageIds.add("msg1");
        messageIds.add("msg2");
        
        // When
        Conversation conversation = Conversation.builder()
                .id("conv456")
                .customerId(customerId)
                .messageIds(messageIds)
                .status(ConversationStatus.ACTIVE)
                .startTime(now)
                .lastActivityTime(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        // Then
        assertNotNull(conversation);
        assertEquals("conv456", conversation.getId());
        assertEquals(customerId, conversation.getCustomerId());
        assertEquals(2, conversation.getMessageIds().size());
        assertTrue(conversation.getMessageIds().contains("msg1"));
        assertTrue(conversation.getMessageIds().contains("msg2"));
        assertEquals(ConversationStatus.ACTIVE, conversation.getStatus());
        assertEquals(now, conversation.getStartTime());
        assertEquals(now, conversation.getLastActivityTime());
        assertEquals(now, conversation.getCreatedAt());
        assertEquals(now, conversation.getUpdatedAt());
    }
    
    @Test
    void shouldCreateEmptyConversation() {
        // Given
        String customerId = "cust789";
        
        // When
        Conversation conversation = Conversation.builder()
                .customerId(customerId)
                .status(ConversationStatus.ACTIVE)
                .build();
        
        // Then
        assertNotNull(conversation);
        assertEquals(customerId, conversation.getCustomerId());
        assertTrue(conversation.getMessageIds().isEmpty());
        assertEquals(ConversationStatus.ACTIVE, conversation.getStatus());
        assertNotNull(conversation.getContext());
    }
    
    @Test
    void shouldModifyConversationProperties() {
        // Given
        Conversation conversation = new Conversation();
        LocalDateTime now = LocalDateTime.now();
        List<String> messageIds = new ArrayList<>();
        messageIds.add("msg3");
        
        // When
        conversation.setId("conv789");
        conversation.setCustomerId("newCust456");
        conversation.setMessageIds(messageIds);
        conversation.setStatus(ConversationStatus.CLOSED);
        conversation.setStartTime(now);
        conversation.setEndTime(now);
        conversation.setLastActivityTime(now);
        conversation.setHandedOffToHuman(true);
        
        // Then
        assertEquals("conv789", conversation.getId());
        assertEquals("newCust456", conversation.getCustomerId());
        assertEquals(1, conversation.getMessageIds().size());
        assertTrue(conversation.getMessageIds().contains("msg3"));
        assertEquals(ConversationStatus.CLOSED, conversation.getStatus());
        assertEquals(now, conversation.getStartTime());
        assertEquals(now, conversation.getEndTime());
        assertEquals(now, conversation.getLastActivityTime());
        assertTrue(conversation.isHandedOffToHuman());
        assertEquals(now, conversation.getClosedAt());
    }
    
    @Test
    void shouldAddMessageToConversation() {
        // Given
        Conversation conversation = Conversation.builder()
                .id("conv123")
                .customerId("cust123")
                .status(ConversationStatus.ACTIVE)
                .build();
        
        // When
        List<String> messageIds = new ArrayList<>(conversation.getMessageIds());
        messageIds.add("newMsg1");
        conversation.setMessageIds(messageIds);
        
        // Then
        assertEquals(1, conversation.getMessageIds().size());
        assertTrue(conversation.getMessageIds().contains("newMsg1"));
    }
} 