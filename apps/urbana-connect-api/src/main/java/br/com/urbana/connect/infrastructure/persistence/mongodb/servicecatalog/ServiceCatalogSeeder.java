package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.application.catalog.ConfiguredCatalogBaseline;
import br.com.urbana.connect.application.catalog.CatalogBaselineProperties;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ServiceCatalogSeeder implements ApplicationRunner {

    private final SpringDataServiceCatalogRepository repository;
    private final ConfiguredCatalogBaseline baseline;

    @Autowired
    public ServiceCatalogSeeder(SpringDataServiceCatalogRepository repository, ConfiguredCatalogBaseline baseline) {
        this.repository = repository;
        this.baseline = baseline;
    }

    /** Compatibility constructor for focused adapter tests. Runtime wiring supplies the configured baseline. */
    public ServiceCatalogSeeder(SpringDataServiceCatalogRepository repository) {
        this(repository, new ConfiguredCatalogBaseline(new CatalogBaselineProperties()));
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ServiceCatalogDocument seed : initialCatalog()) {
            if (!repository.existsByType(seed.getType())) {
                repository.save(seed);
            }
        }
    }

    private List<ServiceCatalogDocument> initialCatalog() {
        return baseline.all().stream()
                .map(this::toDocument)
                .toList();
    }

    private ServiceCatalogDocument toDocument(ServiceCatalogItem item) {
        ServiceCatalogDocument document = new ServiceCatalogDocument();
        document.setType(item.type());
        document.setName(item.name());
        document.setEmoji(item.emoji());
        document.setScenarioText(item.scenarioText());
        document.setPresentationText(item.presentationText());
        document.setPrice(item.price());
        document.setTermsResource(item.termsResource());
        document.setPaymentResource(item.paymentResource());
        document.setBriefingResource(item.briefingResource());
        document.setAreaRule(item.areaRule());
        document.setScope(item.scope());
        document.setDeliverables(item.deliverables());
        document.setProcess(item.process());
        document.setResponsibilities(item.responsibilities());
        document.setExclusions(item.exclusions());
        document.setSupport(item.support());
        document.setAvailable(item.available());
        return document;
    }

}
