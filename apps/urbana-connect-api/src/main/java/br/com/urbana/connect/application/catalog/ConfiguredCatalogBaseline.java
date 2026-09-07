package br.com.urbana.connect.application.catalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.net.URI;

/** Builds a complete, canonical baseline from environment configuration. */
public final class ConfiguredCatalogBaseline {
    private static final List<ServiceType> CANONICAL_TYPES = List.of(
            ServiceType.DECOR_INTERIORES, ServiceType.DECOR_PINTURA,
            ServiceType.DECOR_FACHADA, ServiceType.DECOR_REFORMA);

    private final Map<ServiceType, ServiceCatalogItem> items;

    public ConfiguredCatalogBaseline(CatalogBaselineProperties properties) {
        if (!properties.isEnabled()) {
            this.items = ServiceCatalogItem.canonicalCatalog().stream()
                    .map(item -> properties.isFixtureFallback() ? item : unavailable(item))
                    .collect(() -> new EnumMap<>(ServiceType.class), (map, item) -> map.put(item.type(), item), Map::putAll);
            return;
        }
        Map<ServiceType, ServiceCatalogItem> values = new EnumMap<>(ServiceType.class);
        Map<ServiceType, ServiceCatalogItem> defaults = ServiceCatalogItem.canonicalCatalog().stream()
                .collect(() -> new EnumMap<>(ServiceType.class), (map, item) -> map.put(item.type(), item), Map::putAll);
        for (ServiceType type : CANONICAL_TYPES) {
            CatalogBaselineProperties.ServiceProperties configured = properties.getServices().get(key(type));
            if (configured == null) {
                throw new IllegalStateException("missing operational catalog configuration for " + key(type));
            }
            requireHttpsResource(configured.getTermsResource(), type, "termsResource");
            requireHttpsResource(configured.getPaymentResource(), type, "paymentResource");
            requireHttpsResource(configured.getBriefingResource(), type, "briefingResource");
            if (configured.getPrice() == null || configured.getPrice().signum() < 0) {
                throw new IllegalStateException("invalid operational catalog price for " + key(type));
            }
            ServiceCatalogItem baseline = defaults.get(type);
            values.put(type, new ServiceCatalogItem(type, baseline.name(), baseline.emoji(), baseline.scenarioText(),
                    baseline.presentationText(), configured.getPrice(), configured.getTermsResource(),
                    configured.getPaymentResource(), configured.getBriefingResource(), baseline.areaRule(), baseline.scope(),
                    baseline.deliverables(), baseline.process(), baseline.responsibilities(), baseline.exclusions(),
                    baseline.support(), configured.getAvailable() == null || configured.getAvailable()));
        }
        this.items = Map.copyOf(values);
    }

    public List<ServiceCatalogItem> all() { return CANONICAL_TYPES.stream().map(items::get).toList(); }
    public ServiceCatalogItem byType(ServiceType type) { return items.get(ServiceType.canonicalize(type)); }

    private static String key(ServiceType type) {
        return switch (type) {
            case DECOR_INTERIORES -> "decor-interiores";
            case DECOR_PINTURA -> "decor-pintura";
            case DECOR_FACHADA -> "decor-fachada";
            case DECOR_REFORMA -> "decor-reforma";
            default -> throw new IllegalArgumentException("not a canonical operational service: " + type);
        };
    }

    private static void requireHttpsResource(String value, ServiceType type, String field) {
        if (value == null || value.isBlank() || value.startsWith("https://fixtures.urbana.local/")) {
            throw new IllegalStateException("missing approved " + field + " for " + key(type));
        }
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException("approved " + field + " must be an HTTPS URL for " + key(type));
            }
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("approved " + field + " must be a valid HTTPS URL for " + key(type), malformed);
        }
    }

    private static ServiceCatalogItem unavailable(ServiceCatalogItem item) {
        return new ServiceCatalogItem(item.type(), item.name(), item.emoji(), item.scenarioText(),
                item.presentationText(), item.price(), item.termsResource(), item.paymentResource(),
                item.briefingResource(), item.areaRule(), item.scope(), item.deliverables(), item.process(),
                item.responsibilities(), item.exclusions(), item.support(), false);
    }
}
