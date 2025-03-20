package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.enums.CustomerStatus;
import br.com.urbana.connect.domain.port.input.CustomerManagementUseCase;
import br.com.urbana.connect.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementação do caso de uso de gerenciamento de clientes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService implements CustomerManagementUseCase {
    
    private final CustomerRepository customerRepository;
    
    @Override
    public Customer registerCustomer(Customer customer) {
        log.debug("Registrando novo cliente: {}", customer.getPhoneNumber());
        
        // Verificar se o cliente já existe
        if (customerRepository.existsByPhoneNumber(customer.getPhoneNumber())) {
            log.info("Cliente já existe com o número: {}", customer.getPhoneNumber());
            return customerRepository.findByPhoneNumber(customer.getPhoneNumber()).orElseThrow();
        }
        
        // Configurar valores padrão
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        
        Customer savedCustomer = customerRepository.save(customer);
        log.info("Cliente registrado com sucesso. ID: {}", savedCustomer.getId());
        
        return savedCustomer;
    }
    
    @Override
    public Optional<Customer> findCustomerByPhoneNumber(String phoneNumber) {
        log.debug("Buscando cliente pelo número: {}", phoneNumber);
        return customerRepository.findByPhoneNumber(phoneNumber);
    }
    
    @Override
    public Customer updateCustomer(Customer customer) {
        log.debug("Atualizando cliente: {}", customer.getId());
        
        // Verificar se o cliente existe
        customerRepository.findById(customer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        
        customer.setUpdatedAt(LocalDateTime.now());
        Customer updatedCustomer = customerRepository.save(customer);
        
        log.info("Cliente atualizado com sucesso. ID: {}", updatedCustomer.getId());
        return updatedCustomer;
    }
    
    @Override
    public Customer updateCustomerStatus(String customerId, CustomerStatus status) {
        log.debug("Atualizando status do cliente: {} para {}", customerId, status);
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        
        customer.setStatus(status);
        customer.setUpdatedAt(LocalDateTime.now());
        
        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Status do cliente atualizado com sucesso. ID: {}, Status: {}", 
                updatedCustomer.getId(), updatedCustomer.getStatus());
        
        return updatedCustomer;
    }
    
    @Override
    public Customer setCustomerOptIn(String customerId, boolean optIn) {
        log.debug("Atualizando opt-in do cliente: {} para {}", customerId, optIn);
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        
        customer.setOptedIn(optIn);
        customer.setUpdatedAt(LocalDateTime.now());
        
        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Opt-in do cliente atualizado com sucesso. ID: {}, Opt-in: {}", 
                updatedCustomer.getId(), updatedCustomer.isOptedIn());
        
        return updatedCustomer;
    }
    
    @Override
    public List<Customer> listActiveCustomers() {
        log.debug("Listando todos os clientes ativos");
        return customerRepository.findByStatus(CustomerStatus.ACTIVE);
    }
    
    @Override
    public List<Customer> findAll() {
        log.debug("Buscando todos os clientes");
        return customerRepository.findByStatus(null); // Passando null para tentar buscar todos
    }
    
    @Override
    public Optional<Customer> findById(String id) {
        log.debug("Buscando cliente pelo ID: {}", id);
        return customerRepository.findById(id);
    }
    
    @Override
    public Optional<Customer> findByPhoneNumber(String phoneNumber) {
        log.debug("Buscando cliente pelo número de telefone: {}", phoneNumber);
        return findCustomerByPhoneNumber(phoneNumber);
    }
    
    @Override
    public Customer updatePreferences(String customerId, Map<String, String> preferences) {
        log.debug("Atualizando preferências do cliente: {}", customerId);
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        
        // Adicionar ou substituir preferências
        if (customer.getPreferences() == null) {
            customer.setPreferences(preferences);
        } else {
            customer.getPreferences().putAll(preferences);
        }
        customer.setUpdatedAt(LocalDateTime.now());
        
        return customerRepository.save(customer);
    }
} 