package br.com.urbana.connect.application.controller;

import br.com.urbana.connect.application.dto.CustomerDTO;
import br.com.urbana.connect.domain.port.input.CustomerManagementUseCase;
import br.com.urbana.connect.domain.model.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controlador para gerenciar clientes.
 * Fornece endpoints para listar, buscar e atualizar informações de clientes.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerManagementUseCase customerService;

    /**
     * Lista todos os clientes registrados.
     *
     * @return Lista de clientes convertidos para DTO
     */
    @GetMapping
    public ResponseEntity<List<CustomerDTO>> getAllCustomers() {
        log.info("Buscando todos os clientes");
        
        List<Customer> customers = customerService.findAll();
        List<CustomerDTO> customerDTOs = customers.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(customerDTOs);
    }

    /**
     * Busca um cliente específico pelo ID.
     *
     * @param id ID do cliente
     * @return O cliente encontrado ou 404 se não encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<CustomerDTO> getCustomerById(@PathVariable String id) {
        log.info("Buscando cliente com ID: {}", id);
        
        Optional<Customer> customer = customerService.findById(id);
        
        return customer
                .map(c -> ResponseEntity.ok(convertToDTO(c)))
                .orElseGet(() -> {
                    log.warn("Cliente com ID {} não encontrado", id);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Busca um cliente pelo número de telefone.
     *
     * @param phoneNumber Número de telefone do cliente
     * @return O cliente encontrado ou 404 se não encontrado
     */
    @GetMapping("/phone/{phoneNumber}")
    public ResponseEntity<CustomerDTO> getCustomerByPhoneNumber(@PathVariable String phoneNumber) {
        log.info("Buscando cliente com número de telefone: {}", phoneNumber);
        
        Optional<Customer> customer = customerService.findByPhoneNumber(phoneNumber);
        
        return customer
                .map(c -> ResponseEntity.ok(convertToDTO(c)))
                .orElseGet(() -> {
                    log.warn("Cliente com telefone {} não encontrado", phoneNumber);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Atualiza as informações de um cliente.
     *
     * @param id          ID do cliente
     * @param customerDTO DTO com as informações atualizadas do cliente
     * @return O cliente atualizado
     */
    @PutMapping("/{id}")
    public ResponseEntity<CustomerDTO> updateCustomer(
            @PathVariable String id,
            @RequestBody CustomerDTO customerDTO) {
        
        log.info("Atualizando cliente com ID: {}", id);
        
        try {
            // Validação dos campos obrigatórios
            if (customerDTO.getName() == null || customerDTO.getName().trim().isEmpty()) {
                log.warn("Tentativa de atualização de cliente com nome inválido");
                return ResponseEntity.badRequest().build();
            }
            
            Customer customerToUpdate = Customer.builder()
                    .id(id)
                    .name(customerDTO.getName())
                    .phoneNumber(customerDTO.getPhoneNumber())
                    .email(customerDTO.getEmail())
                    .preferences(customerDTO.getPreferences())
                    .build();
            
            Customer updatedCustomer = customerService.updateCustomer(customerToUpdate);
            return ResponseEntity.ok(convertToDTO(updatedCustomer));
            
        } catch (Exception e) {
            log.error("Erro ao atualizar cliente {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Adiciona preferências a um cliente.
     *
     * @param id          ID do cliente
     * @param preferences Mapa de preferências para adicionar
     * @return O cliente atualizado
     */
    @PatchMapping("/{id}/preferences")
    public ResponseEntity<CustomerDTO> updateCustomerPreferences(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> preferences) {
        
        log.info("Atualizando preferências do cliente com ID: {}", id);
        
        try {
            Customer updatedCustomer = customerService.updatePreferences(id, preferences);
            return ResponseEntity.ok(convertToDTO(updatedCustomer));
        } catch (Exception e) {
            log.error("Erro ao atualizar preferências do cliente {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Converte uma entidade Customer para DTO.
     *
     * @param customer A entidade Customer
     * @return O DTO correspondente
     */
    private CustomerDTO convertToDTO(Customer customer) {
        return CustomerDTO.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phoneNumber(customer.getPhoneNumber())
                .email(customer.getEmail())
                .preferences(customer.getPreferences())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
} 