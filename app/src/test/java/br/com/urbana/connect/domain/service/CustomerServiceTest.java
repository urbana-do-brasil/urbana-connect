package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.domain.enums.CustomerStatus;
import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.port.output.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer testCustomer;
    private final String CUSTOMER_ID = "test-customer-id";
    private final String PHONE_NUMBER = "+5511999999999";

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(CUSTOMER_ID)
                .phoneNumber(PHONE_NUMBER)
                .name("Usuário Teste")
                .email("test@example.com")
                .status(CustomerStatus.ACTIVE)
                .optedIn(true)
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .preferences(new HashMap<>())
                .build();
    }

    @Test
    void registerCustomer_whenNewCustomer_shouldSaveWithDefaultValues() {
        // Given
        Customer newCustomer = Customer.builder()
                .phoneNumber(PHONE_NUMBER)
                .name("Novo Cliente")
                .build();
        
        Customer savedCustomer = Customer.builder()
                .id(CUSTOMER_ID)
                .phoneNumber(PHONE_NUMBER)
                .name("Novo Cliente")
                .status(CustomerStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(customerRepository.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        // When
        Customer result = customerService.registerCustomer(newCustomer);

        // Then
        assertNotNull(result);
        assertEquals(CUSTOMER_ID, result.getId());
        assertEquals(PHONE_NUMBER, result.getPhoneNumber());
        assertEquals(CustomerStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer capturedCustomer = customerCaptor.getValue();
        
        assertEquals(CustomerStatus.ACTIVE, capturedCustomer.getStatus());
        assertNotNull(capturedCustomer.getCreatedAt());
        assertNotNull(capturedCustomer.getUpdatedAt());
    }

    @Test
    void registerCustomer_whenCustomerExists_shouldReturnExistingCustomer() {
        // Given
        Customer existingCustomer = Customer.builder()
                .phoneNumber(PHONE_NUMBER)
                .name("Cliente Existente")
                .build();
        
        when(customerRepository.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(true);
        when(customerRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testCustomer));

        // When
        Customer result = customerService.registerCustomer(existingCustomer);

        // Then
        assertNotNull(result);
        assertEquals(CUSTOMER_ID, result.getId());
        assertEquals(PHONE_NUMBER, result.getPhoneNumber());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void findCustomerByPhoneNumber_whenExists_shouldReturnCustomer() {
        // Given
        when(customerRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testCustomer));

        // When
        Optional<Customer> result = customerService.findCustomerByPhoneNumber(PHONE_NUMBER);

        // Then
        assertTrue(result.isPresent());
        assertEquals(CUSTOMER_ID, result.get().getId());
        assertEquals(PHONE_NUMBER, result.get().getPhoneNumber());
    }

    @Test
    void findCustomerByPhoneNumber_whenNotExists_shouldReturnEmpty() {
        // Given
        when(customerRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());

        // When
        Optional<Customer> result = customerService.findCustomerByPhoneNumber("non-existent-number");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void updateCustomer_whenCustomerExists_shouldUpdateAndReturnCustomer() {
        // Given
        Customer customerToUpdate = Customer.builder()
                .id(CUSTOMER_ID)
                .phoneNumber(PHONE_NUMBER)
                .name("Nome Atualizado")
                .email("updated@example.com")
                .build();
        
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(customerToUpdate);

        // When
        Customer result = customerService.updateCustomer(customerToUpdate);

        // Then
        assertNotNull(result);
        assertEquals(CUSTOMER_ID, result.getId());
        assertEquals("Nome Atualizado", result.getName());
        assertEquals("updated@example.com", result.getEmail());
        
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer capturedCustomer = customerCaptor.getValue();
        
        assertNotNull(capturedCustomer.getUpdatedAt());
    }

    @Test
    void updateCustomer_whenCustomerNotExists_shouldThrowException() {
        // Given
        Customer customerToUpdate = Customer.builder()
                .id("non-existent-id")
                .phoneNumber(PHONE_NUMBER)
                .name("Nome Atualizado")
                .build();
        
        when(customerRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When, Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> customerService.updateCustomer(customerToUpdate)
        );
        
        assertEquals("Cliente não encontrado", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void updateCustomerStatus_whenCustomerExists_shouldUpdateStatus() {
        // Given
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(testCustomer));
        
        Customer updatedCustomer = Customer.builder()
                .id(CUSTOMER_ID)
                .phoneNumber(PHONE_NUMBER)
                .name(testCustomer.getName())
                .email(testCustomer.getEmail())
                .status(CustomerStatus.BLOCKED)
                .optedIn(testCustomer.isOptedIn())
                .createdAt(testCustomer.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .preferences(testCustomer.getPreferences())
                .build();
                
        when(customerRepository.save(any(Customer.class))).thenReturn(updatedCustomer);

        // When
        Customer result = customerService.updateCustomerStatus(CUSTOMER_ID, CustomerStatus.BLOCKED);

        // Then
        assertNotNull(result);
        assertEquals(CustomerStatus.BLOCKED, result.getStatus());
        
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer capturedCustomer = customerCaptor.getValue();
        
        assertEquals(CustomerStatus.BLOCKED, capturedCustomer.getStatus());
        assertNotNull(capturedCustomer.getUpdatedAt());
    }

    @Test
    void updateCustomerStatus_whenCustomerNotExists_shouldThrowException() {
        // Given
        when(customerRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When, Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> customerService.updateCustomerStatus("non-existent-id", CustomerStatus.BLOCKED)
        );
        
        assertEquals("Cliente não encontrado", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void setCustomerOptIn_whenCustomerExists_shouldUpdateOptInStatus() {
        // Given
        testCustomer.setOptedIn(false);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(testCustomer));
        
        Customer updatedCustomer = Customer.builder()
                .id(CUSTOMER_ID)
                .phoneNumber(PHONE_NUMBER)
                .name(testCustomer.getName())
                .email(testCustomer.getEmail())
                .status(testCustomer.getStatus())
                .optedIn(true)
                .createdAt(testCustomer.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .preferences(testCustomer.getPreferences())
                .build();
                
        when(customerRepository.save(any(Customer.class))).thenReturn(updatedCustomer);

        // When
        Customer result = customerService.setCustomerOptIn(CUSTOMER_ID, true);

        // Then
        assertNotNull(result);
        assertTrue(result.isOptedIn());
        
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer capturedCustomer = customerCaptor.getValue();
        
        assertTrue(capturedCustomer.isOptedIn());
        assertNotNull(capturedCustomer.getUpdatedAt());
    }

    @Test
    void setCustomerOptIn_whenCustomerNotExists_shouldThrowException() {
        // Given
        when(customerRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When, Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> customerService.setCustomerOptIn("non-existent-id", true)
        );
        
        assertEquals("Cliente não encontrado", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void listActiveCustomers_shouldReturnOnlyActiveCustomers() {
        // Given
        List<Customer> activeCustomers = Arrays.asList(
                testCustomer,
                Customer.builder().id("customer-2").phoneNumber("+5511888888888").status(CustomerStatus.ACTIVE).build()
        );
        
        when(customerRepository.findByStatus(CustomerStatus.ACTIVE)).thenReturn(activeCustomers);

        // When
        List<Customer> result = customerService.listActiveCustomers();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(CustomerStatus.ACTIVE, result.get(0).getStatus());
        assertEquals(CustomerStatus.ACTIVE, result.get(1).getStatus());
    }

    @Test
    void findAll_shouldCallRepositoryWithNullStatus() {
        // Given
        List<Customer> allCustomers = Arrays.asList(
                testCustomer,
                Customer.builder().id("customer-2").phoneNumber("+5511888888888").status(CustomerStatus.BLOCKED).build(),
                Customer.builder().id("customer-3").phoneNumber("+5511777777777").status(CustomerStatus.INACTIVE).build()
        );
        
        when(customerRepository.findByStatus(null)).thenReturn(allCustomers);

        // When
        List<Customer> result = customerService.findAll();

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        verify(customerRepository).findByStatus(null);
    }

    @Test
    void findById_whenExists_shouldReturnCustomer() {
        // Given
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(testCustomer));

        // When
        Optional<Customer> result = customerService.findById(CUSTOMER_ID);

        // Then
        assertTrue(result.isPresent());
        assertEquals(CUSTOMER_ID, result.get().getId());
    }

    @Test
    void findById_whenNotExists_shouldReturnEmpty() {
        // Given
        when(customerRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When
        Optional<Customer> result = customerService.findById("non-existent-id");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void findByPhoneNumber_shouldCallFindCustomerByPhoneNumber() {
        // Given
        when(customerRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(testCustomer));

        // When
        Optional<Customer> result = customerService.findByPhoneNumber(PHONE_NUMBER);

        // Then
        assertTrue(result.isPresent());
        assertEquals(CUSTOMER_ID, result.get().getId());
    }

    @Test
    void updatePreferences_whenCustomerExists_shouldUpdatePreferencesCorrectly() {
        // Given
        Map<String, String> existingPreferences = new HashMap<>();
        existingPreferences.put("notification", "whatsapp");
        
        Customer customerWithPrefs = Customer.builder()
                .id(CUSTOMER_ID)
                .phoneNumber(PHONE_NUMBER)
                .name(testCustomer.getName())
                .email(testCustomer.getEmail())
                .status(testCustomer.getStatus())
                .optedIn(testCustomer.isOptedIn())
                .createdAt(testCustomer.getCreatedAt())
                .updatedAt(testCustomer.getUpdatedAt())
                .preferences(existingPreferences)
                .build();
        
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerWithPrefs));
        
        Map<String, String> newPreferences = new HashMap<>();
        newPreferences.put("language", "pt_BR");
        newPreferences.put("theme", "dark");
        
        Map<String, String> expectedCombinedPrefs = new HashMap<>();
        expectedCombinedPrefs.put("notification", "whatsapp");
        expectedCombinedPrefs.put("language", "pt_BR");
        expectedCombinedPrefs.put("theme", "dark");
        
        Customer expectedResult = Customer.builder()
                .id(CUSTOMER_ID)
                .phoneNumber(PHONE_NUMBER)
                .name(testCustomer.getName())
                .email(testCustomer.getEmail())
                .status(testCustomer.getStatus())
                .optedIn(testCustomer.isOptedIn())
                .createdAt(testCustomer.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .preferences(expectedCombinedPrefs)
                .build();
        
        when(customerRepository.save(any(Customer.class))).thenReturn(expectedResult);

        // When
        Customer result = customerService.updatePreferences(CUSTOMER_ID, newPreferences);

        // Then
        assertNotNull(result);
        assertNotNull(result.getPreferences());
        assertEquals(3, result.getPreferences().size());
        assertEquals("whatsapp", result.getPreferences().get("notification"));
        assertEquals("pt_BR", result.getPreferences().get("language"));
        assertEquals("dark", result.getPreferences().get("theme"));
        
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer capturedCustomer = customerCaptor.getValue();
        
        assertEquals(3, capturedCustomer.getPreferences().size());
        assertNotNull(capturedCustomer.getUpdatedAt());
    }

    @Test
    void updatePreferences_whenCustomerHasNoPreferences_shouldCreateNewPreferencesMap() {
        // Given
        testCustomer.setPreferences(null);
        
        Map<String, String> newPreferences = new HashMap<>();
        newPreferences.put("language", "pt_BR");
        
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(testCustomer));
        
        Customer expectedResult = Customer.builder()
                .id(CUSTOMER_ID)
                .phoneNumber(PHONE_NUMBER)
                .name(testCustomer.getName())
                .email(testCustomer.getEmail())
                .status(testCustomer.getStatus())
                .optedIn(testCustomer.isOptedIn())
                .createdAt(testCustomer.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .preferences(newPreferences)
                .build();
        
        when(customerRepository.save(any(Customer.class))).thenReturn(expectedResult);

        // When
        Customer result = customerService.updatePreferences(CUSTOMER_ID, newPreferences);

        // Then
        assertNotNull(result);
        assertNotNull(result.getPreferences());
        assertEquals(1, result.getPreferences().size());
        assertEquals("pt_BR", result.getPreferences().get("language"));
        
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer capturedCustomer = customerCaptor.getValue();
        
        assertEquals(newPreferences, capturedCustomer.getPreferences());
    }

    @Test
    void updatePreferences_whenCustomerNotExists_shouldThrowException() {
        // Given
        when(customerRepository.findById("non-existent-id")).thenReturn(Optional.empty());
        
        Map<String, String> preferences = new HashMap<>();
        preferences.put("language", "pt_BR");

        // When, Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> customerService.updatePreferences("non-existent-id", preferences)
        );
        
        assertEquals("Cliente não encontrado", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }
} 