package br.com.urbana.connect.domain.port.input;

import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.enums.CustomerStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface que define os casos de uso para gestão de clientes.
 * Seguindo o padrão de arquitetura hexagonal, esta é uma porta de entrada.
 */
public interface CustomerManagementUseCase {
    
    /**
     * Registra um novo cliente no sistema.
     * 
     * @param customer Dados do cliente a ser registrado
     * @return Cliente registrado com ID gerado
     */
    Customer registerCustomer(Customer customer);
    
    /**
     * Busca um cliente pelo número de telefone.
     * 
     * @param phoneNumber Número de telefone do cliente
     * @return Cliente encontrado ou vazio se não existir
     */
    Optional<Customer> findCustomerByPhoneNumber(String phoneNumber);
    
    /**
     * Atualiza os dados de um cliente existente.
     * 
     * @param customer Cliente com dados atualizados
     * @return Cliente atualizado
     */
    Customer updateCustomer(Customer customer);
    
    /**
     * Altera o status de um cliente.
     * 
     * @param customerId ID do cliente
     * @param status Novo status
     * @return Cliente com status atualizado
     */
    Customer updateCustomerStatus(String customerId, CustomerStatus status);
    
    /**
     * Registra opt-in ou opt-out do cliente para receber mensagens.
     * 
     * @param customerId ID do cliente
     * @param optIn true para opt-in, false para opt-out
     * @return Cliente atualizado
     */
    Customer setCustomerOptIn(String customerId, boolean optIn);
    
    /**
     * Lista todos os clientes ativos.
     * 
     * @return Lista de clientes ativos
     */
    List<Customer> listActiveCustomers();
    
    /**
     * Busca todos os clientes.
     * 
     * @return Lista de todos os clientes
     */
    List<Customer> findAll();
    
    /**
     * Busca um cliente pelo ID.
     * 
     * @param id ID do cliente
     * @return Cliente encontrado ou vazio se não existir
     */
    Optional<Customer> findById(String id);
    
    /**
     * Busca um cliente pelo número de telefone.
     * Equivalente a findCustomerByPhoneNumber, mantido para compatibilidade.
     * 
     * @param phoneNumber Número de telefone do cliente
     * @return Cliente encontrado ou vazio se não existir
     */
    Optional<Customer> findByPhoneNumber(String phoneNumber);
    
    /**
     * Atualiza as preferências de um cliente.
     * 
     * @param customerId ID do cliente
     * @param preferences Preferências a serem atualizadas
     * @return Cliente atualizado
     */
    Customer updatePreferences(String customerId, Map<String, String> preferences);
} 