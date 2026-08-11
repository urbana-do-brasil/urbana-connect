package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.AssembledContext;
import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationContentKey;
import br.com.urbana.connect.domain.conversation.model.ConversationMessage;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.ServiceSummary;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import br.com.urbana.connect.domain.conversation.port.out.ConversationContentGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ConversationContextAssembler {

    private static final int MAX_CONTEXT_CHARS = 5_500;
    private static final String CORE_IDENTITY = """
        A Urba fala como uma atendente humana, próxima e clara.
        Prefere mensagens curtas, objetivas e acolhedoras.
        Usa emojis quando ajudam a dar calor e leveza, sem exagero.
        Nunca deve soar como menu robótico, assistente frio ou atendente prolixo.
        """.stripIndent();

    private static final String OPERATIONAL_POLICY = """
        Máximo de 3 frases curtas por resposta.
        Máximo de 1 pergunta por turno.
        Não repita ou parafraseie o que o cliente acabou de dizer, exceto para confirmar avanço.
        Não use metafala sobre processo.
        Não invente serviço, preço, link ou prazo.
        Se não houver progresso útil, reformule ou ofereça opções estruturadas.
        """.stripIndent();

    private final ConversationContentGateway conversationContentGateway;
    private final ConversationMessageGateway conversationMessageGateway;

    public ConversationContextAssembler(
            ConversationContentGateway conversationContentGateway,
            ConversationMessageGateway conversationMessageGateway) {
        this.conversationContentGateway = conversationContentGateway;
        this.conversationMessageGateway = conversationMessageGateway;
    }

    public AssembledContext assemble(
            Conversation conversation,
            InboundWhatsAppMessage inboundMessage,
            List<ServiceCatalogItem> availableServices,
            StepContract stepContract) {
        String playbook = resolvePlaybook(conversation.currentStep());
        LinkedList<ServiceSummary> businessKnowledge = availableServices.stream()
            .map(service -> new ServiceSummary(
                service.type(),
                service.name(),
                service.scenarioText(),
                service.price(),
                service.available()
            ))
            .collect(Collectors.toCollection(LinkedList::new));
        LinkedList<String> sessionMemory = new LinkedList<>(toSessionMemory(conversation.id(), 10));
        List<String> includedLayers = new ArrayList<>(List.of(
            "coreIdentity",
            "operationalPolicy",
            "conversationPlaybook",
            "businessKnowledge",
            "sessionMemory",
            "currentTurn"
        ));

        int totalSize = CORE_IDENTITY.length()
            + OPERATIONAL_POLICY.length()
            + playbook.length()
            + businessKnowledge.stream().mapToInt(this::serviceSummarySize).sum()
            + sessionMemory.stream().mapToInt(String::length).sum()
            + nullSafeLength(inboundMessage.textBody())
            + stepContract.goal().length();

        while (totalSize > MAX_CONTEXT_CHARS && !sessionMemory.isEmpty()) {
            totalSize -= sessionMemory.removeFirst().length();
        }
        if (sessionMemory.isEmpty()) {
            includedLayers.remove("sessionMemory");
        }

        while (totalSize > MAX_CONTEXT_CHARS && businessKnowledge.size() > 1) {
            ServiceSummary removed = businessKnowledge.removeLast();
            totalSize -= serviceSummarySize(removed);
        }
        if (businessKnowledge.size() < availableServices.size()) {
            includedLayers.remove("businessKnowledge");
            includedLayers.add("businessKnowledge(truncated)");
        }

        if (totalSize > MAX_CONTEXT_CHARS && !playbook.isBlank()) {
            playbook = firstParagraph(playbook);
            includedLayers.remove("conversationPlaybook");
            includedLayers.add("conversationPlaybook(truncated)");
        }

        return new AssembledContext(
            conversation.currentStep(),
            stepContract.goal(),
            inboundMessage.textBody(),
            CORE_IDENTITY,
            OPERATIONAL_POLICY,
            playbook,
            businessKnowledge,
            sessionMemory,
            conversation.context().slots(),
            includedLayers
        );
    }

    private String resolvePlaybook(ConversationStep step) {
        ConversationContentKey key = switch (step) {
            case GREETING -> ConversationContentKey.PLAYBOOK_GREETING;
            case ICP_QUALIFICATION -> ConversationContentKey.PLAYBOOK_ICP_QUALIFICATION;
            case SERVICE_DISCOVERY, TRIAGE_DIRECT, TRIAGE_GUIDED -> ConversationContentKey.PLAYBOOK_SERVICE_DISCOVERY;
            default -> null;
        };
        if (key == null) {
            return "";
        }
        return conversationContentGateway.findActiveValue(key).orElse("");
    }

    private List<String> toSessionMemory(String conversationId, int limit) {
        return conversationMessageGateway.findRecentByConversationId(conversationId, limit).stream()
            .map(this::formatMessage)
            .toList();
    }

    private String formatMessage(ConversationMessage message) {
        return "%s: %s".formatted(
            message.senderType().name(),
            message.rawText() == null || message.rawText().isBlank() ? "sem texto" : message.rawText()
        );
    }

    private int serviceSummarySize(ServiceSummary summary) {
        return summary.type().name().length()
            + nullSafeLength(summary.name())
            + nullSafeLength(summary.scenarioText())
            + nullSafeLength(summary.price() == null ? null : summary.price().toPlainString())
            + Boolean.toString(summary.available()).length();
    }

    private int nullSafeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String firstParagraph(String text) {
        return text.lines().limit(4).collect(Collectors.joining("\n"));
    }
}
