package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ServiceCatalogSeeder implements ApplicationRunner {

    private final SpringDataServiceCatalogRepository repository;

    public ServiceCatalogSeeder(SpringDataServiceCatalogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ServiceCatalogDocument seed : initialCatalog()) {
            repository.findByType(seed.getType())
                    .map(existing -> merge(existing, seed))
                    .ifPresentOrElse(repository::save, () -> repository.save(seed));
        }
    }

    private List<ServiceCatalogDocument> initialCatalog() {
        return ServiceCatalogItem.canonicalCatalog().stream()
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

    private ServiceCatalogDocument merge(ServiceCatalogDocument existing, ServiceCatalogDocument seed) {
        existing.setType(seed.getType());
        existing.setName(seed.getName());
        existing.setEmoji(seed.getEmoji());
        existing.setScenarioText(seed.getScenarioText());
        existing.setPresentationText(seed.getPresentationText());
        existing.setPrice(seed.getPrice());
        existing.setTermsResource(seed.getTermsResource());
        existing.setPaymentResource(seed.getPaymentResource());
        existing.setBriefingResource(seed.getBriefingResource());
        existing.setAreaRule(seed.getAreaRule());
        existing.setScope(seed.getScope());
        existing.setDeliverables(seed.getDeliverables());
        existing.setProcess(seed.getProcess());
        existing.setResponsibilities(seed.getResponsibilities());
        existing.setExclusions(seed.getExclusions());
        existing.setSupport(seed.getSupport());
        existing.setAvailable(seed.isAvailable());
        return existing;
    }
}
