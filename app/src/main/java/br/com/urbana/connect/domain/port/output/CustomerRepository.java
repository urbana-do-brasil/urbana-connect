package br.com.urbana.connect.domain.port.output;

import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.enums.CustomerStatus;

import java.util.List;
import java.util.Optional;

/**
 * Interface para operações de persistência de clientes.
 * Na arquitetura hexagonal, representa uma porta de saída.
 */
public interface CustomerRepository {
    
    /**
     * Salva um cliente.
     * 
     * @param customer Cliente a ser salvo
     * @return Cliente persistido com ID gerado
     */
    Customer save(Customer customer);
    
    /**
     * Busca um cliente pelo ID.
     * 
     * @param id ID do cliente
     * @return Cliente encontrado ou vazio se não existir
     */
    Optional<Customer> findById(String id);
    
    /**
     * Busca um cliente pelo número de telefone.
     * 
     * @param phoneNumber Número de telefone do cliente
     * @return Cliente encontrado ou vazio se não existir
     */
    Optional<Customer> findByPhoneNumber(String phoneNumber);
    
    /**
     * Lista todos os clientes com um determinado status.
     * 
     * @param status Status dos clientes
     * @return Lista de clientes com o status especificado
     */
    List<Customer> findByStatus(CustomerStatus status);
    
    /**
     * Atualiza o status de um cliente.
     * 
     * @param id ID do cliente
     * @param status Novo status
     * @return Cliente atualizado
     */
    Customer updateStatus(String id, CustomerStatus status);
    
    /**
     * Exclui um cliente pelo ID.
     * 
     * @param id ID do cliente
     */
    void deleteById(String id);
    
    /**
     * Verifica se um cliente existe pelo número de telefone.
     * 
     * @param phoneNumber Número de telefone
     * @return true se o cliente existe
     */
    boolean existsByPhoneNumber(String phoneNumber);
} 