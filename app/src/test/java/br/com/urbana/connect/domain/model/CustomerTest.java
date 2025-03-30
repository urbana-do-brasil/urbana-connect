package br.com.urbana.connect.domain.model;

import br.com.urbana.connect.domain.enums.CustomerStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CustomerTest {

    @Test
    void shouldCreateCustomerSuccessfully() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        // When
        Customer customer = Customer.builder()
                .id("123")
                .phoneNumber("+5511999999999")
                .name("João Silva")
                .email("joao@example.com")
                .status(CustomerStatus.ACTIVE)
                .optedIn(true)
                .lastInteraction(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        // Then
        assertNotNull(customer);
        assertEquals("123", customer.getId());
        assertEquals("+5511999999999", customer.getPhoneNumber());
        assertEquals("João Silva", customer.getName());
        assertEquals("joao@example.com", customer.getEmail());
        assertEquals(CustomerStatus.ACTIVE, customer.getStatus());
        assertTrue(customer.isOptedIn());
        assertEquals(now, customer.getLastInteraction());
        assertEquals(now, customer.getCreatedAt());
        assertEquals(now, customer.getUpdatedAt());
        assertNotNull(customer.getPreferences());
        assertTrue(customer.getPreferences().isEmpty());
    }
    
    @Test
    void shouldCreateCustomerWithPreferences() {
        // Given
        Map<String, String> preferences = new HashMap<>();
        preferences.put("language", "pt-br");
        preferences.put("timezone", "America/Sao_Paulo");
        
        // When
        Customer customer = Customer.builder()
                .phoneNumber("+5511999999999")
                .name("Maria Souza")
                .preferences(preferences)
                .build();
        
        // Then
        assertNotNull(customer);
        assertEquals("+5511999999999", customer.getPhoneNumber());
        assertEquals("Maria Souza", customer.getName());
        assertEquals(2, customer.getPreferences().size());
        assertEquals("pt-br", customer.getPreferences().get("language"));
        assertEquals("America/Sao_Paulo", customer.getPreferences().get("timezone"));
    }
    
    @Test
    void shouldChangeCustomerProperties() {
        // Given
        Customer customer = new Customer();
        
        // When
        customer.setId("456");
        customer.setPhoneNumber("+5511888888888");
        customer.setName("Pedro Alves");
        customer.setEmail("pedro@example.com");
        customer.setStatus(CustomerStatus.INACTIVE);
        customer.setOptedIn(false);
        
        // Then
        assertEquals("456", customer.getId());
        assertEquals("+5511888888888", customer.getPhoneNumber());
        assertEquals("Pedro Alves", customer.getName());
        assertEquals("pedro@example.com", customer.getEmail());
        assertEquals(CustomerStatus.INACTIVE, customer.getStatus());
        assertFalse(customer.isOptedIn());
    }
} 