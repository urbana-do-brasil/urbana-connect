package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataServiceCatalogRepository extends MongoRepository<ServiceCatalogDocument, String> {

    boolean existsByType(ServiceType type);

    List<ServiceCatalogDocument> findAllByAvailableTrueOrderByNameAsc();

    Optional<ServiceCatalogDocument> findByType(ServiceType type);
}
