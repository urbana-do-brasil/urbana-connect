package br.com.urbana.connect.infrastructure.persistence.mongodb.conversationcontent;

import br.com.urbana.connect.domain.conversation.model.ConversationContentKey;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ConversationContentSeeder implements ApplicationRunner {

    private final SpringDataConversationContentRepository repository;

    public ConversationContentSeeder(SpringDataConversationContentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ConversationContentDocument content : defaults()) {
            repository.findByKey(content.getKey())
                .ifPresentOrElse(existing -> {
                }, () -> repository.save(content));
        }
    }

    private List<ConversationContentDocument> defaults() {
        return List.of(
            entry(
                ConversationContentKey.GREETING_TEXT,
                "Olá! Tudo bem?\n\nNossas boas-vindas! 💜\n\nSou a Urba e irei te atender hoje. 😃\n\nPrecisa de ajuda para encontrar o serviço perfeito para você?"
            ),
            entry(
                ConversationContentKey.DIRECT_TRIAGE_TEXT,
                "Show! Você já sabe o serviço que deseja. 😄\n\nEntão conta pra gente, para qual opção deseja atendimento:"
            ),
            entry(
                ConversationContentKey.GUIDED_TRIAGE_PROMPT,
                "Das opções abaixo, qual você se identifica mais?"
            ),
            entry(
                ConversationContentKey.TERMS_TEXT,
                "Pra gente iniciar a Decor, o último check é no nosso Termo de Uso 🤝🏾.\n\n"
                    + "Assim deixamos tudo transparente e zero dor de cabeça.\n\n"
                    + "Dá uma olhadinha nele: 👇🏾\n\n"
                    + "{{TERMS_LINK}}\n\n"
                    + "Depois da leitura, você aceita seguir com o termo?"
            ),
            entry(
                ConversationContentKey.PAYMENT_METHOD_TEXT,
                "Você irá realizar o pagamento via PIX ou cartão de crédito?"
            ),
            entry(
                ConversationContentKey.CLOSING_TEXT,
                "Perfeito! Assim que o pagamento for confirmado, daremos os próximos passos 😊"
            ),
            entry(
                ConversationContentKey.HUMAN_HANDOFF_ACK,
                "Iremos repassar sua dúvida para nossa equipe, que entrará em contato logo mais"
            ),
            entry(
                ConversationContentKey.FALLBACK_UNKNOWN_INPUT,
                "Não entendi 😊 Por favor, use as opções abaixo:"
            )
        );
    }

    private ConversationContentDocument entry(ConversationContentKey key, String value) {
        ConversationContentDocument document = new ConversationContentDocument();
        document.setKey(key);
        document.setChannel("WHATSAPP");
        document.setScope("FLOW");
        document.setValue(value);
        document.setActive(true);
        document.setUpdatedAt(Instant.now());
        return document;
    }
}
