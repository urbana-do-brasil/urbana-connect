package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;

import java.util.List;
import java.util.Optional;

public class MongoServiceCatalogGateway implements ServiceCatalogGateway {

    private final SpringDataServiceCatalogRepository repository;

    public MongoServiceCatalogGateway(SpringDataServiceCatalogRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ServiceCatalogItem> findAll() {
        return repository.findAll().stream()
            .filter(document -> document.getType() != ServiceType.DECOR)
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<ServiceCatalogItem> findAvailable() {
        return repository.findAllByAvailableTrueOrderByNameAsc().stream()
            .filter(document -> document.getType() != ServiceType.DECOR)
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<ServiceCatalogItem> findByType(ServiceType type) {
        return repository.findByType(ServiceType.canonicalize(type)).map(this::toDomain);
    }

    private ServiceCatalogItem toDomain(ServiceCatalogDocument document) {
        return new ServiceCatalogItem(
            document.getType(),
            document.getName(),
            document.getEmoji(),
            document.getScenarioText(),
            document.getPresentationText(),
            document.getPrice(),
            document.getTermsResource(),
            document.getPaymentResource(),
            document.getBriefingResource(),
            document.getAreaRule(),
            document.getScope(),
            document.getDeliverables(),
            document.getProcess(),
            document.getResponsibilities(),
            document.getExclusions(),
            document.getSupport(),
            document.isAvailable());
    }
}
