package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationContentKey;
import br.com.urbana.connect.domain.conversation.model.ConversationMessage;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageDirection;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageSenderType;
import br.com.urbana.connect.domain.conversation.model.ConversationMessageType;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotValue;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.port.out.ConversationContentGateway;
import br.com.urbana.connect.domain.conversation.port.out.ConversationMessageGateway;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationContextAssemblerTest {

    @Test
    void shouldAssembleContextWithAllCoreLayers() {
        ConversationContentGateway contentGateway = mock(ConversationContentGateway.class);
        ConversationMessageGateway messageGateway = mock(ConversationMessageGateway.class);
        ConversationContextAssembler assembler = new ConversationContextAssembler(contentGateway, messageGateway);

        when(contentGateway.findActiveValue(eq(ConversationContentKey.PLAYBOOK_GREETING)))
            .thenReturn(Optional.of("Playbook greeting"));
        when(messageGateway.findRecentByConversationId(any(), eq(10))).thenReturn(List.of(
            ConversationMessage.inbound("conv-1", "+5583", ConversationMessageType.TEXT, "Oi", null, "wamid-1", Instant.now(), "GREETING")
        ));

        Conversation conversation = Conversation.start("+5583999999999", Instant.now())
            .withContext(Conversation.start("+5583999999999", Instant.now()).context().withSlot(
                ConversationSlotName.NEEDS_DISCOVERY_HELP,
                new ConversationSlotValue("true", ConversationSlotLevel.CONFIRMED, ConversationSlotSource.EXPLICIT, 1.0)
            ), Instant.now());
        StepContractRegistry registry = new StepContractRegistry();

        var assembled = assembler.assemble(
            conversation,
            new InboundWhatsAppMessage("+5583999999999", "preciso de ajuda", ""),
            List.of(decor()),
            registry.findByStep(ConversationStep.GREETING).orElseThrow()
        );

        assertThat(assembled.coreIdentity()).contains("atendente humana");
        assertThat(assembled.operationalPolicy()).contains("Máximo de 3 frases");
        assertThat(assembled.conversationPlaybook()).contains("Playbook greeting");
        assertThat(assembled.businessKnowledge()).hasSize(1);
        assertThat(assembled.sessionMemory()).hasSize(1);
        assertThat(assembled.includedLayers()).contains("coreIdentity", "operationalPolicy", "conversationPlaybook", "businessKnowledge", "sessionMemory", "currentTurn");
    }

    @Test
    void shouldTruncateSessionMemoryWhenContextGetsTooLarge() {
        ConversationContentGateway contentGateway = mock(ConversationContentGateway.class);
        ConversationMessageGateway messageGateway = mock(ConversationMessageGateway.class);
        ConversationContextAssembler assembler = new ConversationContextAssembler(contentGateway, messageGateway);

        when(contentGateway.findActiveValue(any())).thenReturn(Optional.of("playbook"));
        when(messageGateway.findRecentByConversationId(any(), eq(10))).thenReturn(List.of(
            outbound("Mensagem muito longa ".repeat(120)),
            outbound("Outra mensagem muito longa ".repeat(120)),
            outbound("Mais contexto ".repeat(120))
        ));

        StepContractRegistry registry = new StepContractRegistry();
        var assembled = assembler.assemble(
            Conversation.start("+5583999999999", Instant.now()),
            new InboundWhatsAppMessage("+5583999999999", "Oi".repeat(400), ""),
            List.of(decor(), decorPintura(), decorFachada()),
            registry.findByStep(ConversationStep.GREETING).orElseThrow()
        );

        assertThat(assembled.includedLayers()).contains("currentTurn");
        assertThat(assembled.sessionMemory().size()).isLessThan(3);
    }

    private ConversationMessage outbound(String text) {
        return new ConversationMessage(
            "id",
            "conv-1",
            "+5583",
            "WHATSAPP",
            ConversationMessageDirection.OUTBOUND,
            ConversationMessageSenderType.URBA_BOT,
            ConversationMessageType.TEXT,
            text,
            null,
            null,
            Instant.now(),
            "GREETING"
        );
    }

    private ServiceCatalogItem decor() {
        return service(ServiceType.DECOR, "Decor", "Renovar espaço interno", "490.00");
    }

    private ServiceCatalogItem decorPintura() {
        return service(ServiceType.DECOR_PINTURA, "Decor Pintura", "Renovar com pintura", "390.00");
    }

    private ServiceCatalogItem decorFachada() {
        return service(ServiceType.DECOR_FACHADA, "Decor Fachada", "Renovar fachada", "590.00");
    }

    private ServiceCatalogItem service(ServiceType type, String name, String scenario, String price) {
        return new ServiceCatalogItem(type, name, "✨", scenario, "presentation", new BigDecimal(price), "https://pay.example/" + type.name(), null, true);
    }
}
