package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotSource;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiAction;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.model.SlotRequirement;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import br.com.urbana.connect.domain.conversation.model.StepFallbackBehavior;
import br.com.urbana.connect.domain.conversation.model.StructuredEscapeType;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationResponseValidatorTest {

    private final ConversationResponseValidator validator = new ConversationResponseValidator();

    @Test
    void shouldAcceptValidReply() {
        ResponseValidationResult result = validator.validate(
            stepContract(),
            new ConversationalAiReply(
                "Decor parece a melhor opção para isso 😊",
                ConversationalAiAction.PROPOSE_SERVICE,
                List.of(new ConversationSlotUpdate(
                    ConversationSlotName.SUGGESTED_SERVICE,
                    "DECOR",
                    ConversationSlotLevel.TENTATIVE,
                    0.92,
                    ConversationSlotSource.INFERRED
                )),
                0.92,
                true,
                ConversationStep.AWAITING_CONFIRMATION,
                false,
                null
            ),
            List.of(decor())
        );

        assertThat(result.valid()).isTrue();
    }

    @Test
    void shouldAcceptLegacyDecorAliasWhenCanonicalCatalogContainsDecorInteriores() {
        ResponseValidationResult result = validator.validate(
            stepContract(),
            new ConversationalAiReply(
                "Decor parece a melhor opção para isso 😊",
                ConversationalAiAction.PROPOSE_SERVICE,
                List.of(new ConversationSlotUpdate(
                    ConversationSlotName.SUGGESTED_SERVICE,
                    "DECOR",
                    ConversationSlotLevel.TENTATIVE,
                    0.92,
                    ConversationSlotSource.INFERRED
                )),
                0.92,
                true,
                ConversationStep.AWAITING_CONFIRMATION,
                false,
                null
            ),
            List.of(canonicalDecorInteriores())
        );

        assertThat(result.valid()).isTrue();
    }

    @Test
    void shouldRejectReplyWithMetaSpeech() {
        ResponseValidationResult result = validator.validate(
            stepContract(),
            new ConversationalAiReply(
                "Agora vou coletar mais informações sobre o seu projeto.",
                ConversationalAiAction.ASK_CLARIFYING_QUESTION,
                List.of(),
                0.7,
                false,
                null,
                false,
                null
            ),
            List.of(decor())
        );

        assertThat(result.reason()).isEqualTo("meta_speech");
    }

    @Test
    void shouldRejectReplyWithUnknownServiceMention() {
        ResponseValidationResult result = validator.validate(
            stepContract(),
            new ConversationalAiReply(
                "Acho que Projeto Premium parece a melhor opção para isso.",
                ConversationalAiAction.PROPOSE_SERVICE,
                List.of(),
                0.9,
                false,
                null,
                false,
                null
            ),
            List.of(decor())
        );

        assertThat(result.reason()).isEqualTo("unknown_service_mention");
    }

    @Test
    void shouldRejectUnknownServiceMentionEvenWhenActionIsNotProposeService() {
        ResponseValidationResult result = validator.validate(
            stepContract(),
            new ConversationalAiReply(
                "Projeto Premium parece uma ótima opção para o que você descreveu.",
                ConversationalAiAction.CONFIRM_UNDERSTANDING,
                List.of(),
                0.9,
                false,
                null,
                false,
                null
            ),
            List.of(decor())
        );

        assertThat(result.reason()).isEqualTo("unknown_service_mention");
    }

    @Test
    void shouldRejectReplyWithDivergentPrice() {
        ResponseValidationResult result = validator.validate(
            stepContract(),
            new ConversationalAiReply(
                "Esse serviço custa R$ 999,00.",
                ConversationalAiAction.CONFIRM_UNDERSTANDING,
                List.of(),
                0.88,
                false,
                null,
                false,
                null
            ),
            List.of(decor())
        );

        assertThat(result.reason()).isEqualTo("divergent_price");
    }

    @Test
    void shouldRejectReplyWithTooManyQuestions() {
        ResponseValidationResult result = validator.validate(
            stepContract(),
            new ConversationalAiReply(
                "Você quer ajuda? É sua primeira vez contratando isso?",
                ConversationalAiAction.ASK_CLARIFYING_QUESTION,
                List.of(),
                0.88,
                false,
                null,
                false,
                null
            ),
            List.of(decor())
        );

        assertThat(result.reason()).isEqualTo("too_many_questions");
    }

    @Test
    void shouldAcceptGenericReplyThatDoesNotMentionAnyService() {
        ResponseValidationResult result = validator.validate(
            stepContract(),
            new ConversationalAiReply(
                "Entendi 😊 Me conta um pouco mais do ambiente que você quer transformar.",
                ConversationalAiAction.ASK_CLARIFYING_QUESTION,
                List.of(),
                0.88,
                false,
                null,
                false,
                null
            ),
            List.of(decor())
        );

        assertThat(result.valid()).isTrue();
    }

    private StepContract stepContract() {
        return new StepContract(
            ConversationStep.SERVICE_DISCOVERY,
            "descobrir serviço",
            List.of(new SlotRequirement(ConversationSlotName.SUGGESTED_SERVICE, ConversationSlotLevel.TENTATIVE)),
            List.of(),
            Set.of(
                ConversationalAiAction.ASK_CLARIFYING_QUESTION,
                ConversationalAiAction.CONFIRM_UNDERSTANDING,
                ConversationalAiAction.PROPOSE_SERVICE,
                ConversationalAiAction.OFFER_STRUCTURED_OPTIONS,
                ConversationalAiAction.ACKNOWLEDGE_AND_ADVANCE,
                ConversationalAiAction.REPEAT_WITH_REFRAME
            ),
            Set.of(),
            StepFallbackBehavior.REPEAT_SERVICE_DISCOVERY_WITH_OPTIONS,
            3,
            StructuredEscapeType.SERVICE_DISCOVERY_OPTIONS,
            false
        );
    }

    private ServiceCatalogItem decor() {
        return new ServiceCatalogItem(
            ServiceType.DECOR,
            "Decor",
            "✨",
            "Renovar espaço interno",
            "Apresentação Decor",
            new BigDecimal("490.00"),
            "https://pay.example/decor",
            null,
            true
        );
    }

    private ServiceCatalogItem canonicalDecorInteriores() {
        return new ServiceCatalogItem(
            ServiceType.DECOR_INTERIORES,
            "Decor Interiores",
            "🛋️",
            "Renovar espaço interno",
            "Apresentação Decor Interiores",
            new BigDecimal("490.00"),
            "https://pay.example/decor-interiores",
            null,
            true
        );
    }
}
