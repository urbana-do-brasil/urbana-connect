package br.com.urbana.connect.infrastructure.persistence;

import br.com.urbana.connect.domain.model.Customer;
import br.com.urbana.connect.domain.enums.CustomerStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Interface Spring Data MongoDB para Customer.
 */
@Repository
public interface CustomerMongoRepository extends MongoRepository<Customer, String> {
    
    Optional<Customer> findByPhoneNumber(String phoneNumber);
    
    List<Customer> findByStatus(CustomerStatus status);
    
    boolean existsByPhoneNumber(String phoneNumber);
} 