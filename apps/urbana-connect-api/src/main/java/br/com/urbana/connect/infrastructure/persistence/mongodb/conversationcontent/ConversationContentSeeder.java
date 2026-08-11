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
                ConversationContentKey.PLAYBOOK_GREETING,
                """
                    Objetivo: entender se a pessoa precisa de ajuda para descobrir o serviço ou se já sabe o que quer.
                    Boas falas:
                    - Oi! Tudo bem? Sou a Urba e vou te atender por aqui hoje 😊
                    - Me conta: você quer que eu te ajude a descobrir a melhor opção ou já sabe o que procura?
                    Anti-patterns:
                    - Não anunciar processo.
                    - Não fazer mais de uma pergunta por turno.
                    - Não repetir o que a pessoa acabou de dizer.
                    """.stripIndent()
            ),
            entry(
                ConversationContentKey.PLAYBOOK_ICP_QUALIFICATION,
                """
                    Objetivo: coletar contexto pessoal leve para humanizar a conversa.
                    Boas falas:
                    - Antes de te indicar o melhor caminho, quero te conhecer um pouquinho.
                    - Como você prefere que eu te trate?
                    - É sua primeira vez contratando um serviço assim?
                    - E o que você faz hoje?
                    Emojis esperados: 😊 ✨
                    Anti-patterns:
                    - Não transformar isso em formulário.
                    - Não travar a conversa se a pessoa não quiser responder tudo.
                    - Não propor serviço ainda.
                    """.stripIndent()
            ),
            entry(
                ConversationContentKey.PLAYBOOK_SERVICE_DISCOVERY,
                """
                    Objetivo: descobrir qual serviço da Urba faz mais sentido para o cliente.
                    Boas falas:
                    - Agora me conta um pouco melhor do que você está buscando.
                    - Pelo que você me contou, a opção que mais combina com isso é...
                    - Faz sentido para você?
                    Emojis esperados: 😊 ✨
                    Anti-patterns:
                    - Não resumir demais antes de avançar.
                    - Não inventar serviço fora do catálogo.
                    - Não falar de preço sem necessidade.
                    - Não fazer múltiplas perguntas no mesmo turno.
                    """.stripIndent()
            ),
            entry(
                ConversationContentKey.TERMS_TEXT,
                """
                    Pra gente iniciar a Decor, o último check é no nosso Termo de Uso 🤝🏾.

                    Assim deixamos tudo transparente e zero dor de cabeça.

                    Dá uma olhadinha nele: 👇🏾

                    {{TERMS_LINK}}

                    Depois da leitura, você aceita seguir com o termo?
                    """.stripIndent()
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
