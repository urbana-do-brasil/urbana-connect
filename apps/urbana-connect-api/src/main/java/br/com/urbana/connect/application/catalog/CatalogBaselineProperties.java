package br.com.urbana.connect.application.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operational values for the catalog. These deliberately contain no usable
 * defaults: enabling the operational catalog without the approved artifacts
 * must stop the application rather than expose fixture links.
 */
@ConfigurationProperties(prefix = "catalog.operational")
public class CatalogBaselineProperties {
    private boolean enabled;
    /**
     * Fixture links are useful for isolated local tests only. HML/PROD set
     * this to false so an incomplete operational baseline cannot be offered to
     * a customer while the feature is being provisioned.
     */
    private boolean fixtureFallback = true;
    private Map<String, ServiceProperties> services = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isFixtureFallback() { return fixtureFallback; }
    public void setFixtureFallback(boolean fixtureFallback) { this.fixtureFallback = fixtureFallback; }
    public Map<String, ServiceProperties> getServices() { return services; }
    public void setServices(Map<String, ServiceProperties> services) {
        this.services = services == null ? new LinkedHashMap<>() : new LinkedHashMap<>(services);
    }

    public static class ServiceProperties {
        private BigDecimal price;
        private String termsResource;
        private String paymentResource;
        private String briefingResource;
        private Boolean available;

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getTermsResource() { return termsResource; }
        public void setTermsResource(String termsResource) { this.termsResource = termsResource; }
        public String getPaymentResource() { return paymentResource; }
        public void setPaymentResource(String paymentResource) { this.paymentResource = paymentResource; }
        public String getBriefingResource() { return briefingResource; }
        public void setBriefingResource(String briefingResource) { this.briefingResource = briefingResource; }
        public Boolean getAvailable() { return available; }
        public void setAvailable(Boolean available) { this.available = available; }
    }
}
