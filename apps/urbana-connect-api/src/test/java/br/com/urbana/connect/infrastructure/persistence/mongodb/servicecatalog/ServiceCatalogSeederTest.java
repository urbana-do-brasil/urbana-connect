package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCatalogSeederTest {

    @Test
    void seedsTheSameFourRichCanonicalServicesAsThePolicy() {
        var repository = mock(SpringDataServiceCatalogRepository.class);
        when(repository.findByType(any(ServiceType.class))).thenReturn(Optional.empty());
        var seeder = new ServiceCatalogSeeder(repository);

        seeder.run(null);

        var captor = org.mockito.ArgumentCaptor.forClass(ServiceCatalogDocument.class);
        verify(repository, org.mockito.Mockito.times(4)).save(captor.capture());
        var saved = captor.getAllValues();
        assertThat(saved).extracting(ServiceCatalogDocument::getType)
                .containsExactly(
                        ServiceType.DECOR_INTERIORES,
                        ServiceType.DECOR_PINTURA,
                        ServiceType.DECOR_FACHADA,
                        ServiceType.DECOR_REFORMA);
        assertThat(saved).allSatisfy(item -> {
            assertThat(item.isAvailable()).isTrue();
            assertThat(item.getAreaRule()).isNotNull();
            assertThat(item.getDeliverables()).contains("Manual PDF", "Tour Virtual");
            assertThat(item.getProcess()).anyMatch(value -> value.contains("Google Meet"));
            assertThat(item.getProcess()).anyMatch(value -> value.contains("7 dias úteis"));
            assertThat(item.getTermsResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.getPaymentResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.getBriefingResource()).startsWith("https://fixtures.urbana.local/");
        });
    }

    @Test
    void refreshesLegacyCopyFieldsAndApprovedFixtureResourcesFromCanonicalCatalog() {
        var repository = mock(SpringDataServiceCatalogRepository.class);
        var existing = new ServiceCatalogDocument();
        existing.setType(ServiceType.DECOR_PINTURA);
        existing.setName("cópia antiga");
        existing.setPaymentLink("https://mpago.la/legacy");
        existing.setBriefingLink("https://forms.gle/legacy");
        existing.setAvailable(false);
        when(repository.findByType(ServiceType.DECOR_PINTURA)).thenReturn(Optional.of(existing));
        when(repository.findByType(ServiceType.DECOR_INTERIORES)).thenReturn(Optional.empty());
        when(repository.findByType(ServiceType.DECOR_FACHADA)).thenReturn(Optional.empty());
        when(repository.findByType(ServiceType.DECOR_REFORMA)).thenReturn(Optional.empty());
        var seeder = new ServiceCatalogSeeder(repository);

        seeder.run(null);

        verify(repository, org.mockito.Mockito.times(4)).save(any(ServiceCatalogDocument.class));
        assertThat(existing.getName()).isEqualTo("Decor Pintura");
        assertThat(existing.isAvailable()).isTrue();
        assertThat(existing.getPaymentResource()).startsWith("https://fixtures.urbana.local/");
        assertThat(existing.getBriefingResource()).startsWith("https://fixtures.urbana.local/");
        assertThat(existing.getPaymentResource()).doesNotContain("mpago.la");
        assertThat(existing.getBriefingResource()).doesNotContain("forms.gle");
    }
}
