package br.com.urbana.connect.domain.servicecatalog.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceCatalogItemTest {

    @Test
    void canonicalCatalogContainsFourAvailableRichServicesAndNoLegacyAlias() {
        var catalog = ServiceCatalogItem.canonicalCatalog();

        assertThat(catalog)
                .hasSize(4)
                .extracting(ServiceCatalogItem::type)
                .containsExactly(
                        ServiceType.DECOR_INTERIORES,
                        ServiceType.DECOR_PINTURA,
                        ServiceType.DECOR_FACHADA,
                        ServiceType.DECOR_REFORMA);
        assertThat(catalog).allSatisfy(item -> {
            assertThat(item.available()).isTrue();
            assertThat(item.termsResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.paymentResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.briefingResource()).startsWith("https://fixtures.urbana.local/");
            assertThat(item.deliverables()).containsExactly(
                    "Manual PDF", "Tour Virtual", "3 opções de solução", "2 rodadas consolidadas");
            assertThat(item.process()).anyMatch(value -> value.equals("briefing"));
            assertThat(item.process()).anyMatch(value -> value.equals("medidas, fotos e vídeos"));
            assertThat(item.process()).anyMatch(value -> value.contains("Google Meet"));
            assertThat(item.process()).anyMatch(value -> value.equals("produção"));
            assertThat(item.process()).anyMatch(value -> value.contains("7 dias úteis"));
            assertThat(item.process()).anyMatch(value -> value.contains("e-mail"));
            assertThat(item.process()).contains(
                    "pagamento integral antecipado",
                    "validação humana do comprovante",
                    "validação dos dados pela arquiteta",
                    "agendamento pelo link de disponibilidade da arquiteta",
                    "pausa do prazo enquanto o cliente estiver pendente de feedback ou aprovação",
                    "aprovação final explícita",
                    "suporte de 3 meses pelo WhatsApp");
            assertThat(item.responsibilities()).isNotEmpty();
            assertThat(item.exclusions()).isNotEmpty();
            assertThat(item.support()).contains("3 meses").containsIgnoringCase("sem visita");
        });
    }

    @Test
    void canonicalFactsMatchCommercialMatrixAndAreaRules() {
        var byType = ServiceCatalogItem.canonicalCatalog().stream()
                .collect(java.util.stream.Collectors.toMap(ServiceCatalogItem::type, item -> item));

        assertThat(byType.get(ServiceType.DECOR_INTERIORES).price()).isEqualByComparingTo(new BigDecimal("400"));
        assertThat(byType.get(ServiceType.DECOR_INTERIORES).areaRule())
                .isEqualTo(AreaRule.UP_TO_20_SQM_PER_ENVIRONMENT);
        assertThat(byType.get(ServiceType.DECOR_PINTURA).price()).isEqualByComparingTo(new BigDecimal("250"));
        assertThat(byType.get(ServiceType.DECOR_PINTURA).areaRule())
                .isEqualTo(AreaRule.UNLIMITED_BY_CATALOG);
        assertThat(byType.get(ServiceType.DECOR_FACHADA).price()).isEqualByComparingTo(new BigDecimal("350"));
        assertThat(byType.get(ServiceType.DECOR_FACHADA).areaRule())
                .isEqualTo(AreaRule.UNLIMITED_BY_CATALOG);
        assertThat(byType.get(ServiceType.DECOR_REFORMA).price()).isEqualByComparingTo(new BigDecimal("450"));
        assertThat(byType.get(ServiceType.DECOR_REFORMA).areaRule())
                .isEqualTo(AreaRule.UP_TO_20_SQM_PER_ENVIRONMENT);

        assertThat(byType.get(ServiceType.DECOR_PINTURA).scope())
                .contains("pintura", "desenhos", "tintas")
                .doesNotContain("20 m²");
        assertThat(byType.get(ServiceType.DECOR_FACHADA).scope())
                .containsIgnoringCase("fachada")
                .contains("externa")
                .doesNotContain("20 m²");
    }
}
