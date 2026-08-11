package br.com.urbana.connect.infrastructure.persistence.mongodb.servicecatalog;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
public class ServiceCatalogSeeder implements ApplicationRunner {

    private final SpringDataServiceCatalogRepository repository;

    public ServiceCatalogSeeder(SpringDataServiceCatalogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ServiceCatalogDocument service : initialCatalog()) {
            repository.findByType(service.getType())
                .map(existing -> merge(existing, service))
                .ifPresentOrElse(repository::save, () -> repository.save(service));
        }
    }

    private List<ServiceCatalogDocument> initialCatalog() {
        return List.of(
            withLinks(service(
                ServiceType.DECOR,
                "Decor",
                "🛋️",
                "Quero renovar meu espaço interno sem gastar muito, nada de quebra-quebra.",
                "Para espaços de até 20m², temos a Decor 🛋️\n\n"
                    + "Criamos uma solução de espaço, de acordo com seu estilo e orçamento.\n\n"
                    + "Inclusive, se você coloca a mão na massa, para economizar, criamos uma solução faça você mesmo, com vídeos e tutoriais.",
                new BigDecimal("400.00"),
                true), "https://mpago.la/1TbJFYx", "https://forms.gle/W4zBPwusPZeJ2cnD7"),
            withLinks(service(
                ServiceType.DECOR_PINTURA,
                "Decor Pintura",
                "🎨",
                "Quero renovar meu espaço com uma pintura, nada de quebra-quebra.",
                "Para renovar gastando pouco, com tintas e estilo, temos a Decor Pintura 🎨\n\n"
                    + "Criamos uma solução de pintura para o seu espaço, com todos os detalhes para você ou seu pintor.\n\n"
                    + "E, se você topa colocar a mão na massa, enviamos nosso manual de pintura didático, com links e tutoriais.",
                new BigDecimal("250.00"),
                true), "https://mpago.la/32aNZUw", "https://forms.gle/6FWqQCxmUxVKc6xG7"),
            withLinks(service(
                ServiceType.DECOR_FACHADA,
                "Decor Fachada",
                "🏡",
                "Quero renovar minha fachada ou muro externo sem gastar muito.",
                "Para fachadas ou muros externos, temos a Decor Fachada 🏡\n\nCriamos uma solução de renovação para a fachada ou muro externo da sua casa ou pequeno negócio.",
                new BigDecimal("350.00"),
                true), "https://mpago.la/1Qeg34y", "https://forms.gle/VXEVPeNUPxKfrWxb6"),
            service(
                ServiceType.DECOR_REFORMA,
                "Decor Reforma",
                "🧱",
                "Quero reformar meu espaço, com quebra-quebra e tudo mais.",
                "Para uma reforma completa de um espaço, temos a Decor Reforma 🧱\n\nCriamos uma solução para reforma completa de um espaço interno, como mudança de layout, paredes, janelas, bancada, elétrica e revestimentos.",
                new BigDecimal("450.00"),
                false)
        );
    }

    private ServiceCatalogDocument service(
            ServiceType type,
            String name,
            String emoji,
            String scenarioText,
            String presentationText,
            BigDecimal price,
            boolean available) {
        ServiceCatalogDocument document = new ServiceCatalogDocument();
        document.setType(type);
        document.setName(name);
        document.setEmoji(emoji);
        document.setScenarioText(scenarioText);
        document.setPresentationText(presentationText);
        document.setPrice(price);
        document.setAvailable(available);
        return document;
    }

    private ServiceCatalogDocument withLinks(
            ServiceCatalogDocument document,
            String paymentLink,
            String briefingLink) {
        document.setPaymentLink(paymentLink);
        document.setBriefingLink(briefingLink);
        return document;
    }

    private ServiceCatalogDocument merge(ServiceCatalogDocument existing, ServiceCatalogDocument seed) {
        existing.setName(seed.getName());
        existing.setEmoji(seed.getEmoji());
        existing.setScenarioText(seed.getScenarioText());
        existing.setPresentationText(seed.getPresentationText());
        existing.setPrice(seed.getPrice());

        if (isBlank(existing.getPaymentLink())) {
            existing.setPaymentLink(seed.getPaymentLink());
        }
        if (isBlank(existing.getBriefingLink())) {
            existing.setBriefingLink(seed.getBriefingLink());
        }
        if (!existing.isAvailable() && seed.isAvailable() && Objects.equals(existing.getPaymentLink(), seed.getPaymentLink())) {
            existing.setAvailable(true);
        }
        return existing;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
