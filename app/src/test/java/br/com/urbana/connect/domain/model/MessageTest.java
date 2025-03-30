package br.com.urbana.connect.domain.model;

import br.com.urbana.connect.domain.enums.MessageDirection;
import br.com.urbana.connect.domain.enums.MessageStatus;
import br.com.urbana.connect.domain.enums.MessageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void shouldCreateMessageSuccessfully() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        String conversationId = "conv123";
        String customerId = "cust456";
        
        // When
        Message message = Message.builder()
                .id("msg789")
                .conversationId(conversationId)
                .customerId(customerId)
                .content("Olá, gostaria de mais informações")
                .direction(MessageDirection.INBOUND)
                .type(MessageType.TEXT)
                .timestamp(now)
                .status(MessageStatus.SENT)
                .build();
        
        // Then
        assertNotNull(message);
        assertEquals("msg789", message.getId());
        assertEquals(conversationId, message.getConversationId());
        assertEquals(customerId, message.getCustomerId());
        assertEquals("Olá, gostaria de mais informações", message.getContent());
        assertEquals(MessageDirection.INBOUND, message.getDirection());
        assertEquals(now, message.getTimestamp());
        assertEquals(MessageStatus.SENT, message.getStatus());
    }
    
    @Test
    void shouldCreateOutboundMessage() {
        // Given
        String conversationId = "conv123";
        String customerId = "cust456";
        
        // When
        Message message = Message.builder()
                .conversationId(conversationId)
                .customerId(customerId)
                .content("Como posso ajudar?")
                .direction(MessageDirection.OUTBOUND)
                .type(MessageType.TEXT)
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();
        
        // Then
        assertNotNull(message);
        assertEquals(conversationId, message.getConversationId());
        assertEquals(customerId, message.getCustomerId());
        assertEquals("Como posso ajudar?", message.getContent());
        assertEquals(MessageDirection.OUTBOUND, message.getDirection());
        assertEquals(MessageStatus.SENT, message.getStatus());
    }
    
    @Test
    void shouldModifyMessageProperties() {
        // Given
        Message message = new Message();
        LocalDateTime now = LocalDateTime.now();
        
        // When
        message.setId("msg123");
        message.setConversationId("newConv456");
        message.setCustomerId("newCust789");
        message.setContent("Mensagem atualizada");
        message.setDirection(MessageDirection.OUTBOUND);
        message.setType(MessageType.TEXT);
        message.setTimestamp(now);
        message.setStatus(MessageStatus.DELIVERED);
        
        // Then
        assertEquals("msg123", message.getId());
        assertEquals("newConv456", message.getConversationId());
        assertEquals("newCust789", message.getCustomerId());
        assertEquals("Mensagem atualizada", message.getContent());
        assertEquals(MessageDirection.OUTBOUND, message.getDirection());
        assertEquals(now, message.getTimestamp());
        assertEquals(MessageStatus.DELIVERED, message.getStatus());
    }
} 