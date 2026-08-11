package br.com.urbana.connect.application.catalog;

import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog.MongoServiceCatalogGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog.SpringDataServiceCatalogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceCatalogConfiguration {

    @Bean
    public ServiceCatalogGateway serviceCatalogGateway(SpringDataServiceCatalogRepository repository) {
        return new MongoServiceCatalogGateway(repository);
    }
}
