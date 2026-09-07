package br.com.urbana.connect.application.catalog;

import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog.MongoServiceCatalogGateway;
import br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog.SpringDataServiceCatalogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(CatalogBaselineProperties.class)
public class ServiceCatalogConfiguration {

    @Bean
    public ConfiguredCatalogBaseline configuredCatalogBaseline(CatalogBaselineProperties properties) {
        return new ConfiguredCatalogBaseline(properties);
    }

    @Bean
    public ServiceCatalogGateway serviceCatalogGateway(SpringDataServiceCatalogRepository repository,
                                                       ConfiguredCatalogBaseline baseline) {
        return new OperationalServiceCatalogGateway(new MongoServiceCatalogGateway(repository), baseline);
    }
}
