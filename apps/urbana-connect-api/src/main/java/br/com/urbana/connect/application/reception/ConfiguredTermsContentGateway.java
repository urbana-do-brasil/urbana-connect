package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.port.out.TermsContentGateway;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable application-boundary view of the approved terms properties.  An
 * absent property intentionally resolves to empty so a fixture or invented
 * legal text can never be shown as production content.
 */
public final class ConfiguredTermsContentGateway implements TermsContentGateway {
    private final Map<String, TermsContent> documents;

    public ConfiguredTermsContentGateway(Map<String, TermsContent> values) {
        Map<String, TermsContent> copy = new HashMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    copy.put(key(key, value.resource()), value);
                }
            });
        }
        documents = Map.copyOf(copy);
    }

    @Override
    public Optional<TermsContent> find(String serviceType, String termsResource) {
        if (termsResource == null || termsResource.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(documents.get(key(serviceType, termsResource)));
    }

    private static String key(String serviceType, String resource) {
        return (serviceType == null ? "" : serviceType.trim().toUpperCase()) + "|" + resource.trim();
    }
}
