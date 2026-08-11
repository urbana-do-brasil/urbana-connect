package br.com.urbana.connect.domain.servicecatalog.port.out;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;

import java.util.List;
import java.util.Optional;

public interface ServiceCatalogGateway {

    List<ServiceCatalogItem> findAll();

    List<ServiceCatalogItem> findAvailable();

    Optional<ServiceCatalogItem> findByType(ServiceType type);
}
