package br.com.urbana.connect.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationContextTest {

    @Test
    void shouldCreateConversationContextSuccessfully() {
        // Given
        List<String> entities = new ArrayList<>();
        entities.add("produto");
        entities.add("preço");
        
        // When
        ConversationContext context = ConversationContext.builder()
                .customerIntent("Comprar produto")
                .lastDetectedTopic("Informações de produto")
                .identifiedEntities(entities)
                .needsHumanIntervention(false)
                .gptContext("Cliente está buscando informações sobre produtos")
                .build();
        
        // Then
        assertNotNull(context);
        assertEquals("Comprar produto", context.getCustomerIntent());
        assertEquals("Informações de produto", context.getLastDetectedTopic());
        assertEquals(2, context.getIdentifiedEntities().size());
        assertTrue(context.getIdentifiedEntities().contains("produto"));
        assertTrue(context.getIdentifiedEntities().contains("preço"));
        assertFalse(context.isNeedsHumanIntervention());
        assertEquals("Cliente está buscando informações sobre produtos", context.getGptContext());
    }
    
    @Test
    void shouldCreateEmptyConversationContext() {
        // When
        ConversationContext context = new ConversationContext();
        
        // Then
        assertNotNull(context);
        assertNull(context.getCustomerIntent());
        assertNull(context.getLastDetectedTopic());
        assertNotNull(context.getIdentifiedEntities());
        assertTrue(context.getIdentifiedEntities().isEmpty());
        assertFalse(context.isNeedsHumanIntervention());
        assertNull(context.getGptContext());
    }
    
    @Test
    void shouldModifyConversationContextProperties() {
        // Given
        ConversationContext context = new ConversationContext();
        List<String> entities = new ArrayList<>();
        entities.add("suporte");
        
        // When
        context.setCustomerIntent("Solicitar suporte");
        context.setLastDetectedTopic("Problemas técnicos");
        context.setIdentifiedEntities(entities);
        context.setNeedsHumanIntervention(true);
        context.setGptContext("Cliente relatando problemas técnicos, precisa de suporte humano");
        
        // Then
        assertEquals("Solicitar suporte", context.getCustomerIntent());
        assertEquals("Problemas técnicos", context.getLastDetectedTopic());
        assertEquals(1, context.getIdentifiedEntities().size());
        assertTrue(context.getIdentifiedEntities().contains("suporte"));
        assertTrue(context.isNeedsHumanIntervention());
        assertEquals("Cliente relatando problemas técnicos, precisa de suporte humano", context.getGptContext());
    }
} 