package br.com.urbana.connect.domain.reception.port.out;

import java.util.Optional;

/**
 * Resolves the approved, versioned legal artifact.  Keeping this behind a port
 * means a fixture cannot silently become a production legal document.
 */
public interface TermsContentGateway {
    Optional<TermsContent> find(String serviceType, String termsResource);

    record TermsContent(String version, String resource, String content) {
        public TermsContent {
            if (version == null || version.isBlank() || resource == null || resource.isBlank()
                    || content == null || content.isBlank()) {
                throw new IllegalArgumentException("terms content fields are required");
            }
        }
    }
}
