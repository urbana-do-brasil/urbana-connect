package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.Conversation;
import br.com.urbana.connect.domain.conversation.model.ConversationContext;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotUpdate;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotValue;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiReply;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class ConversationPolicyEngine {

    private static final double MIN_CONFIDENCE_TO_ADVANCE = 0.85;

    public boolean shouldTriggerStructuredEscape(Conversation conversation, StepContract stepContract) {
        return conversation.context().turnsWithoutProgress() >= stepContract.maxTurnsWithoutProgress();
    }

    public ConversationPolicyDecision decide(
            Conversation conversation,
            StepContract stepContract,
            ConversationalAiReply reply,
            ResponseValidationResult validationResult,
            Conversation updatedConversation,
            Instant receivedAt) {
        if (!validationResult.valid()) {
            return new ConversationPolicyDecision(
                ConversationPolicyDecisionType.APPLY_FALLBACK,
                incrementWithoutProgress(conversation, receivedAt),
                reply,
                validationResult.reason()
            );
        }

        boolean progressed = hasProgress(conversation.context().slots(), updatedConversation.context().slots())
            || updatedConversation.currentStep() != conversation.currentStep();

        if (reply.shouldAdvance() && canAdvance(updatedConversation, stepContract, reply)) {
            Conversation reset = resetWithoutProgress(updatedConversation, receivedAt);
            return new ConversationPolicyDecision(
                ConversationPolicyDecisionType.ACCEPT_AND_ADVANCE,
                reset,
                reply,
                "advance_allowed"
            );
        }

        Conversation nextConversation = progressed
            ? resetWithoutProgress(updatedConversation, receivedAt)
            : incrementWithoutProgress(updatedConversation, receivedAt);

        if (!progressed && nextConversation.context().turnsWithoutProgress() >= stepContract.maxTurnsWithoutProgress()) {
            return new ConversationPolicyDecision(
                ConversationPolicyDecisionType.TRIGGER_STRUCTURED_ESCAPE,
                resetWithoutProgress(nextConversation, receivedAt),
                reply,
                "stagnation_limit_reached"
            );
        }

        return new ConversationPolicyDecision(
            ConversationPolicyDecisionType.ACCEPT_REPLY,
            nextConversation,
            reply,
            progressed ? "reply_with_progress" : "reply_without_progress"
        );
    }

    private boolean canAdvance(Conversation conversation, StepContract contract, ConversationalAiReply reply) {
        if (contract.deterministic()) {
            return false;
        }
        if (reply.confidence() == null || reply.confidence() < MIN_CONFIDENCE_TO_ADVANCE) {
            return false;
        }
        if (reply.suggestedNextStep() == null || !isAllowedNextStep(contract.step(), reply.suggestedNextStep())) {
            return false;
        }
        return contract.requiredSlots().stream().allMatch(requirement ->
            conversation.context().hasSlotAtLeast(requirement.slot(), requirement.minimumLevel())
        );
    }

    private boolean isAllowedNextStep(ConversationStep current, ConversationStep suggestedNextStep) {
        return switch (current) {
            case GREETING -> suggestedNextStep == ConversationStep.ICP_QUALIFICATION;
            case ICP_QUALIFICATION -> suggestedNextStep == ConversationStep.SERVICE_DISCOVERY;
            case SERVICE_DISCOVERY, TRIAGE_DIRECT, TRIAGE_GUIDED -> suggestedNextStep == ConversationStep.AWAITING_CONFIRMATION;
            default -> false;
        };
    }

    private boolean hasProgress(
            Map<ConversationSlotName, ConversationSlotValue> previousSlots,
            Map<ConversationSlotName, ConversationSlotValue> updatedSlots) {
        if (updatedSlots.size() > previousSlots.size()) {
            return true;
        }
        return updatedSlots.entrySet().stream().anyMatch(entry -> !entry.getValue().equals(previousSlots.get(entry.getKey())));
    }

    private Conversation incrementWithoutProgress(Conversation conversation, Instant receivedAt) {
        ConversationContext context = conversation.context()
            .withTurnsWithoutProgress(conversation.currentStep(), conversation.context().turnsWithoutProgress() + 1);
        return conversation.withContext(context, receivedAt);
    }

    private Conversation resetWithoutProgress(Conversation conversation, Instant receivedAt) {
        return conversation.withContext(
            conversation.context().withTurnsWithoutProgress(conversation.currentStep(), 0),
            receivedAt
        );
    }
}
