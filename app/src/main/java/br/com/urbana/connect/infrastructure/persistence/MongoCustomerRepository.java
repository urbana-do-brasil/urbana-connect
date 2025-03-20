package br.com.urbana.connect.infrastructure.persistence;

import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.enums.CustomerStatus;
import br.com.urbana.connect.domain.port.output.CustomerRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementação do repositório de clientes usando MongoDB.
 */
@Repository
public class MongoCustomerRepository implements CustomerRepository {
    
    private final CustomerMongoRepository repository;
    
    public MongoCustomerRepository(CustomerMongoRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public Customer save(Customer customer) {
        return repository.save(customer);
    }
    
    @Override
    public Optional<Customer> findById(String id) {
        return repository.findById(id);
    }
    
    @Override
    public Optional<Customer> findByPhoneNumber(String phoneNumber) {
        return repository.findByPhoneNumber(phoneNumber);
    }
    
    @Override
    public List<Customer> findByStatus(CustomerStatus status) {
        return repository.findByStatus(status);
    }
    
    @Override
    public Customer updateStatus(String id, CustomerStatus status) {
        Customer customer = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        
        customer.setStatus(status);
        customer.setUpdatedAt(LocalDateTime.now());
        
        return repository.save(customer);
    }
    
    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }
    
    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return repository.existsByPhoneNumber(phoneNumber);
    }
} 