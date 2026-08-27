package br.com.urbana.connect.domain.servicecatalog.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record ServiceCatalogItem(
        ServiceType type,
        String name,
        String emoji,
        String scenarioText,
        String presentationText,
        BigDecimal price,
        String termsResource,
        String paymentResource,
        String briefingResource,
        AreaRule areaRule,
        String scope,
        List<String> deliverables,
        List<String> process,
        List<String> responsibilities,
        List<String> exclusions,
        String support,
        boolean available) {

    private static final List<String> COMMON_DELIVERABLES = List.of(
            "Manual PDF",
            "Tour Virtual",
            "3 opções de solução",
            "2 rodadas consolidadas");

    private static final List<String> COMMON_PROCESS = List.of(
            "briefing",
            "pagamento integral antecipado",
            "validação humana do comprovante",
            "medidas, fotos e vídeos",
            "validação dos dados pela arquiteta",
            "agendamento pelo link de disponibilidade da arquiteta",
            "Google Meet",
            "produção",
            "7 dias úteis a partir do início da produção",
            "pausa do prazo enquanto o cliente estiver pendente de feedback ou aprovação",
            "aprovação final explícita",
            "entrega por e-mail do Manual PDF e do Tour Virtual",
            "suporte de 3 meses pelo WhatsApp");

    private static final List<String> COMMON_RESPONSIBILITIES = List.of(
            "Urba: triagem, termos, pagamento, briefing e orientações gerais.",
            "Arquiteta: validação, reunião, produção, opções, ajustes, Manual, Tour e exceções.",
            "Cliente: aceite, pagamento, briefing, medidas, fotos, vídeos, materiais, profissionais e execução.");

    private static final List<String> COMMON_EXCLUSIONS = List.of(
            "A Urbana presta consultoria online e não executa obra ou pintura.",
            "A Urbana especifica, mas não compra materiais nem contrata profissionais.",
            "Estoque, preços, links de compra e disponibilidade de mobiliário não são garantidos.",
            "Uma terceira rodada de ajustes é exceção da arquiteta e não é prometida.",
            "O suporte não inclui visita, gestão de obra ou garantia do resultado.");

    /** Compatibility constructor for existing non-canonical adapters and tests. */
    public ServiceCatalogItem(ServiceType type,
                              String name,
                              String emoji,
                              String scenarioText,
                              String presentationText,
                              BigDecimal price,
                              String paymentLink,
                              String briefingLink,
                              boolean available) {
        this(type, name, emoji, scenarioText, presentationText, price, null, paymentLink, briefingLink,
                AreaRule.UNLIMITED_BY_CATALOG, scenarioText, List.of(), List.of(), List.of(), List.of(),
                "legacy adapter", available);
    }

    public ServiceCatalogItem {
        type = Objects.requireNonNull(type, "type");
        requireText(name, "name");
        requireText(emoji, "emoji");
        requireText(scenarioText, "scenarioText");
        requireText(presentationText, "presentationText");
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("price must be non-negative");
        }
        areaRule = Objects.requireNonNull(areaRule, "areaRule");
        requireText(scope, "scope");
        deliverables = immutableCopy(deliverables);
        process = immutableCopy(process);
        responsibilities = immutableCopy(responsibilities);
        exclusions = immutableCopy(exclusions);
        requireText(support, "support");
    }

    public String paymentLink() {
        return paymentResource;
    }

    public String briefingLink() {
        return briefingResource;
    }

    public String termsLink() {
        return termsResource;
    }

    public String description() {
        return scope;
    }

    public BigDecimal areaLimitSqm() {
        return areaRule.maximumSquareMeters();
    }

    public boolean isFixtureResource() {
        return isFixture(termsResource) && isFixture(paymentResource) && isFixture(briefingResource);
    }

    public static List<ServiceCatalogItem> approvedCatalog() {
        return canonicalCatalog();
    }

    public static List<ServiceCatalogItem> canonicalCatalog() {
        return List.of(
                service(
                        ServiceType.DECOR_INTERIORES,
                        "Decor Interiores",
                        "🛋️",
                        "Ambiente interno com layout, mobiliário, cores, materiais e composição; sem intervenção estrutural.",
                        new BigDecimal("400.00"),
                        AreaRule.UP_TO_20_SQM_PER_ENVIRONMENT,
                        "decor-interiores"),
                service(
                        ServiceType.DECOR_PINTURA,
                        "Decor Pintura",
                        "🎨",
                        "Pintura, desenhos e especificação de tintas; não inclui layout, mobiliário nem ensino prático de pintura.",
                        new BigDecimal("250.00"),
                        AreaRule.UNLIMITED_BY_CATALOG,
                        "decor-pintura"),
                service(
                        ServiceType.DECOR_FACHADA,
                        "Decor Fachada",
                        "🏡",
                        "Fachada, muro ou parede externa; pode considerar revestimentos, portão, iluminação e paisagismo conforme decisão da arquiteta.",
                        new BigDecimal("350.00"),
                        AreaRule.UNLIMITED_BY_CATALOG,
                        "decor-fachada"),
                service(
                        ServiceType.DECOR_REFORMA,
                        "Decor Reforma",
                        "🧱",
                        "Solução para reforma interna; demandas técnicas específicas dependem de avaliação da arquiteta.",
                        new BigDecimal("450.00"),
                        AreaRule.UP_TO_20_SQM_PER_ENVIRONMENT,
                        "decor-reforma"));
    }

    private static ServiceCatalogItem service(ServiceType type,
                                              String name,
                                              String emoji,
                                              String scope,
                                              BigDecimal price,
                                              AreaRule areaRule,
                                              String resourceKey) {
        String area = areaRule.description();
        String presentation = scope + " Área: " + area + ".";
        String fixtureBase = "https://fixtures.urbana.local/";
        return new ServiceCatalogItem(
                type,
                name,
                emoji,
                scope,
                presentation,
                price,
                fixtureBase + "terms/" + resourceKey,
                fixtureBase + "payment/" + resourceKey,
                fixtureBase + "briefing/" + resourceKey,
                areaRule,
                scope,
                COMMON_DELIVERABLES,
                COMMON_PROCESS,
                COMMON_RESPONSIBILITIES,
                COMMON_EXCLUSIONS,
                "Suporte de 3 meses após a entrega para dúvidas sobre o Manual e cores, sem visita ou gestão de obra.",
                true);
    }

    private static List<String> immutableCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static boolean isFixture(String resource) {
        return resource != null && resource.startsWith("https://fixtures.urbana.local/");
    }
}
