package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationPolicyEngineTest {

    private final ConversationPolicyEngine policyEngine = new ConversationPolicyEngine();

    @Test
    void shouldAcceptAdvanceWhenRequiredSlotIsFilled() {
        Instant now = Instant.parse("2026-05-01T12:00:00Z");
        Conversation conversation = Conversation.start("+5583999999999", now);
        Conversation updated = conversation.withContext(
            conversation.context().withSlot(
                ConversationSlotName.NEEDS_DISCOVERY_HELP,
                new br.com.urbana.connect.domain.conversation.model.ConversationSlotValue(
                    "true",
                    ConversationSlotLevel.CONFIRMED,
                    ConversationSlotSource.EXPLICIT,
                    1.0
                )
            ),
            now
        );

        ConversationPolicyDecision decision = policyEngine.decide(
            conversation,
            greetingContract(),
            new ConversationalAiReply(
                "Perfeito 😊",
                ConversationalAiAction.ACKNOWLEDGE_AND_ADVANCE,
                List.of(new ConversationSlotUpdate(
                    ConversationSlotName.NEEDS_DISCOVERY_HELP,
                    "true",
                    ConversationSlotLevel.CONFIRMED,
                    0.95,
                    ConversationSlotSource.EXPLICIT
                )),
                0.95,
                true,
                ConversationStep.ICP_QUALIFICATION,
                false,
                null
            ),
            ResponseValidationResult.accepted(),
            updated,
            now
        );

        assertThat(decision.type()).isEqualTo(ConversationPolicyDecisionType.ACCEPT_AND_ADVANCE);
    }

    @Test
    void shouldTriggerStructuredEscapeWhenLimitIsReachedWithoutProgress() {
        Instant now = Instant.parse("2026-05-01T12:00:00Z");
        Conversation conversation = Conversation.start("+5583999999999", now)
            .moveTo(ConversationStep.SERVICE_DISCOVERY, now)
            .withContext(Conversation.start("+5583999999999", now).context()
                .withTurnsWithoutProgress(ConversationStep.SERVICE_DISCOVERY, 2), now);

        ConversationPolicyDecision decision = policyEngine.decide(
            conversation,
            serviceDiscoveryContract(),
            new ConversationalAiReply(
                "Me conta um pouco mais.",
                ConversationalAiAction.REPEAT_WITH_REFRAME,
                List.of(),
                0.6,
                false,
                null,
                false,
                null
            ),
            ResponseValidationResult.accepted(),
            conversation,
            now
        );

        assertThat(decision.type()).isEqualTo(ConversationPolicyDecisionType.TRIGGER_STRUCTURED_ESCAPE);
    }

    @Test
    void shouldApplyFallbackWhenValidationRejectsReply() {
        Instant now = Instant.parse("2026-05-01T12:00:00Z");
        Conversation conversation = Conversation.start("+5583999999999", now);

        ConversationPolicyDecision decision = policyEngine.decide(
            conversation,
            greetingContract(),
            ConversationalAiReply.fallback("bad_reply"),
            ResponseValidationResult.rejected("meta_speech"),
            conversation,
            now
        );

        assertThat(decision.type()).isEqualTo(ConversationPolicyDecisionType.APPLY_FALLBACK);
        assertThat(decision.reason()).isEqualTo("meta_speech");
    }

    private StepContract greetingContract() {
        return new StepContract(
            ConversationStep.GREETING,
            "entender se precisa de ajuda",
            List.of(new SlotRequirement(ConversationSlotName.NEEDS_DISCOVERY_HELP, ConversationSlotLevel.CONFIRMED)),
            List.of(),
            Set.of(
                ConversationalAiAction.ASK_CLARIFYING_QUESTION,
                ConversationalAiAction.CONFIRM_UNDERSTANDING,
                ConversationalAiAction.ACKNOWLEDGE_AND_ADVANCE,
                ConversationalAiAction.OFFER_STRUCTURED_OPTIONS,
                ConversationalAiAction.REPEAT_WITH_REFRAME
            ),
            Set.of(ConversationalAiAction.PROPOSE_SERVICE),
            StepFallbackBehavior.REPEAT_GREETING_WITH_BUTTONS,
            2,
            StructuredEscapeType.GREETING_HELP_BUTTONS,
            false
        );
    }

    private StepContract serviceDiscoveryContract() {
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
}
