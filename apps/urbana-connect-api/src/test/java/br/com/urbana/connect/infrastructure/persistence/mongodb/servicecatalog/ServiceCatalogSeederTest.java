package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCatalogSeederTest {

    @Test
    void seedsTheSameFourRichCanonicalServicesAsThePolicy() {
        var repository = mock(SpringDataServiceCatalogRepository.class);
        when(repository.existsByType(any(ServiceType.class))).thenReturn(false);
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
            assertThat(item.getDeliverables()).contains("Manual do Espaço em PDF", "Tour Virtual");
            assertThat(item.getProcess()).anyMatch(value -> value.contains("Google Meet"));
            assertThat(item.getProcess()).anyMatch(value -> value.contains("7 dias úteis"));
            assertThat(item.getTermsResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.getPaymentResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.getBriefingResource()).startsWith("https://fixtures.urbana.local/");
        });
    }

    @Test
    void preservesExistingOperationalFieldsInsteadOfOverwritingThem() {
        var repository = mock(SpringDataServiceCatalogRepository.class);
        var existing = new ServiceCatalogDocument();
        existing.setType(ServiceType.DECOR_PINTURA);
        existing.setName("cópia antiga");
        existing.setPaymentLink("https://mpago.la/legacy");
        existing.setBriefingLink("https://forms.gle/legacy");
        existing.setAvailable(false);
        when(repository.existsByType(ServiceType.DECOR_PINTURA)).thenReturn(true);
        when(repository.existsByType(ServiceType.DECOR_INTERIORES)).thenReturn(false);
        when(repository.existsByType(ServiceType.DECOR_FACHADA)).thenReturn(false);
        when(repository.existsByType(ServiceType.DECOR_REFORMA)).thenReturn(false);
        var seeder = new ServiceCatalogSeeder(repository);

        seeder.run(null);

        verify(repository, org.mockito.Mockito.times(3)).save(any(ServiceCatalogDocument.class));
        assertThat(existing.getName()).isEqualTo("cópia antiga");
        assertThat(existing.isAvailable()).isFalse();
        assertThat(existing.getPaymentResource()).isEqualTo("https://mpago.la/legacy");
        assertThat(existing.getBriefingResource()).isEqualTo("https://forms.gle/legacy");
    }
}
