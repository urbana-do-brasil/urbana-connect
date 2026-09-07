package br.com.urbana.connect.application.catalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import br.com.urbana.connect.domain.servicecatalog.port.out.ServiceCatalogGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.net.URI;

/** Mongo is the operational override; the configured baseline is safe continuity. */
public final class OperationalServiceCatalogGateway implements ServiceCatalogGateway {
    private static final Logger log = LoggerFactory.getLogger(OperationalServiceCatalogGateway.class);
    private final ServiceCatalogGateway mongo;
    private final ConfiguredCatalogBaseline baseline;

    public OperationalServiceCatalogGateway(ServiceCatalogGateway mongo, ConfiguredCatalogBaseline baseline) {
        this.mongo = mongo;
        this.baseline = baseline;
    }

    @Override public List<ServiceCatalogItem> findAll() { return effective(false); }
    @Override public List<ServiceCatalogItem> findAvailable() { return effective(true); }

    @Override
    public Optional<ServiceCatalogItem> findByType(ServiceType type) {
        ServiceType canonical = ServiceType.canonicalize(type);
        try {
            return mongo.findByType(canonical)
                    .filter(this::safeOperationalItem)
                    .or(() -> Optional.ofNullable(baseline.byType(canonical)));
        } catch (RuntimeException failure) {
            log.warn("catalog Mongo lookup failed; using configured baseline for type={}", canonical, failure);
            return Optional.ofNullable(baseline.byType(canonical));
        }
    }

    private List<ServiceCatalogItem> effective(boolean availableOnly) {
        LinkedHashMap<ServiceType, ServiceCatalogItem> values = new LinkedHashMap<>();
        baseline.all().forEach(item -> values.put(item.type(), item));
        try {
            mongo.findAll().stream().filter(item -> baseline.byType(item.type()) != null)
                    .filter(this::safeOperationalItem)
                    .forEach(item -> values.put(ServiceType.canonicalize(item.type()), item));
        } catch (RuntimeException failure) {
            log.warn("catalog Mongo lookup failed; using configured baseline", failure);
        }
        return values.values().stream().filter(item -> !availableOnly || item.available()).toList();
    }

    private boolean safeOperationalItem(ServiceCatalogItem item) {
        if (item == null || !item.type().isCanonical()) {
            return false;
        }
        if (!item.available()) {
            return true;
        }
        return approvedHttps(item.termsResource()) && approvedHttps(item.paymentResource())
                && approvedHttps(item.briefingResource());
    }

    private static boolean approvedHttps(String resource) {
        if (resource == null || resource.isBlank() || resource.startsWith("https://fixtures.urbana.local/")) {
            return false;
        }
        try {
            URI uri = URI.create(resource);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }
}
