package br.com.urbana.connect.domain.servicecatalog.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCatalogItemTest {

    @Test
    void protectsCatalogInvariantsAndDistinguishesFixtureResources() {
        ServiceCatalogItem legacy = new ServiceCatalogItem(
                ServiceType.DECOR, "Decor", "🏠", "escopo", "apresentação",
                new BigDecimal("10"), "https://example.test/payment", "https://example.test/briefing", true);

        assertThat(legacy.paymentLink()).isEqualTo("https://example.test/payment");
        assertThat(legacy.briefingLink()).isEqualTo("https://example.test/briefing");
        assertThat(legacy.termsLink()).isNull();
        assertThat(legacy.description()).isEqualTo("escopo");
        assertThat(legacy.areaLimitSqm()).isNull();
        assertThat(legacy.isFixtureResource()).isFalse();

        ServiceCatalogItem immutable = new ServiceCatalogItem(
                ServiceType.DECOR_PINTURA, "Pintura", "🎨", "escopo", "apresentação",
                new BigDecimal("10"), "https://fixtures.urbana.local/terms/pintura",
                "https://fixtures.urbana.local/payment/pintura", "https://fixtures.urbana.local/briefing/pintura",
                AreaRule.UNLIMITED_BY_CATALOG, "escopo", List.of("entrega"), List.of("processo"),
                List.of("responsabilidade"), List.of("exclusão"), "suporte", true);
        assertThat(immutable.isFixtureResource()).isTrue();
        assertThatThrownBy(() -> immutable.deliverables().add("outra"))
                .isInstanceOf(UnsupportedOperationException.class);

        ServiceCatalogItem nullableCollections = new ServiceCatalogItem(
                ServiceType.DECOR, "Decor", "🏠", "escopo", "apresentação", BigDecimal.ONE,
                null, null, null, AreaRule.UNLIMITED_BY_CATALOG, "escopo", null, null, null, null,
                "suporte", true);
        assertThat(nullableCollections.deliverables()).isEmpty();
        assertThat(nullableCollections.process()).isEmpty();
        assertThat(nullableCollections.responsibilities()).isEmpty();
        assertThat(nullableCollections.exclusions()).isEmpty();

        assertThatThrownBy(() -> new ServiceCatalogItem(
                ServiceType.DECOR, "", "🏠", "escopo", "apresentação", BigDecimal.ONE,
                null, null, null, AreaRule.UNLIMITED_BY_CATALOG, "escopo", null, null, null, null,
                "suporte", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ServiceCatalogItem(
                ServiceType.DECOR, null, "🏠", "escopo", "apresentação", BigDecimal.ONE,
                null, null, null, AreaRule.UNLIMITED_BY_CATALOG, "escopo", null, null, null, null,
                "suporte", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ServiceCatalogItem(
                ServiceType.DECOR, "Decor", "🏠", "escopo", "apresentação", new BigDecimal("-1"),
                null, null, null, AreaRule.UNLIMITED_BY_CATALOG, "escopo", null, null, null, null,
                "suporte", true)).isInstanceOf(IllegalArgumentException.class);
    }

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
                    "Manual do Espaço em PDF", "Tour Virtual", "3 opções de solução", "2 rodadas de alterações ou ajustes");
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
        assertThat(byType.get(ServiceType.DECOR_REFORMA).scope())
                .containsIgnoringCase("reforma")
                .containsIgnoringCase("ambiente interno")
                .containsIgnoringCase("arquiteta")
                .contains("20 m²");
        assertThat(byType.get(ServiceType.DECOR_REFORMA).deliverables())
                .contains("Manual do Espaço em PDF", "Tour Virtual", "3 opções de solução",
                        "2 rodadas de alterações ou ajustes");
        assertThat(byType.get(ServiceType.DECOR_REFORMA).support())
                .contains("3 meses", "WhatsApp", "Manual", "cores")
                .containsIgnoringCase("sem visita")
                .containsIgnoringCase("sem gestão de obra");
        String reformExclusions = String.join(" ", byType.get(ServiceType.DECOR_REFORMA).exclusions());
        assertThat(reformExclusions).containsIgnoringCase("não executa a obra");
        assertThat(reformExclusions).containsIgnoringCase("não compra materiais");
        assertThat(reformExclusions).containsIgnoringCase("nem contrata profissionais");
    }
}
