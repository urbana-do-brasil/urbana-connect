package br.com.urbana.connect.application.conversation;

import br.com.urbana.connect.domain.conversation.model.ConversationSlotLevel;
import br.com.urbana.connect.domain.conversation.model.ConversationSlotName;
import br.com.urbana.connect.domain.conversation.model.ConversationStep;
import br.com.urbana.connect.domain.conversation.model.ConversationalAiAction;
import br.com.urbana.connect.domain.conversation.model.SlotRequirement;
import br.com.urbana.connect.domain.conversation.model.StepContract;
import br.com.urbana.connect.domain.conversation.model.StepFallbackBehavior;
import br.com.urbana.connect.domain.conversation.model.StructuredEscapeType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class StepContractRegistry {

    private final Map<ConversationStep, StepContract> contracts = new EnumMap<>(ConversationStep.class);

    public StepContractRegistry() {
        contracts.put(ConversationStep.GREETING, new StepContract(
            ConversationStep.GREETING,
            "entender se o cliente precisa de ajuda para descobrir o serviço ou se já sabe o que quer",
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
        ));

        contracts.put(ConversationStep.ICP_QUALIFICATION, new StepContract(
            ConversationStep.ICP_QUALIFICATION,
            "coletar contexto pessoal leve para humanizar e qualificar a conversa",
            List.of(),
            List.of(
                new SlotRequirement(ConversationSlotName.PRONOUN_PREFERENCE, ConversationSlotLevel.TENTATIVE),
                new SlotRequirement(ConversationSlotName.FIRST_TIME_HIRING_DESIGNER, ConversationSlotLevel.TENTATIVE),
                new SlotRequirement(ConversationSlotName.OCCUPATION, ConversationSlotLevel.TENTATIVE)
            ),
            Set.of(
                ConversationalAiAction.ASK_CLARIFYING_QUESTION,
                ConversationalAiAction.CONFIRM_UNDERSTANDING,
                ConversationalAiAction.ACKNOWLEDGE_AND_ADVANCE,
                ConversationalAiAction.REPEAT_WITH_REFRAME
            ),
            Set.of(
                ConversationalAiAction.PROPOSE_SERVICE,
                ConversationalAiAction.OFFER_STRUCTURED_OPTIONS
            ),
            StepFallbackBehavior.REPEAT_ICP_WITH_REFRAME,
            4,
            StructuredEscapeType.ICP_ADVANCE_TO_DISCOVERY,
            false
        ));

        contracts.put(ConversationStep.SERVICE_DISCOVERY, new StepContract(
            ConversationStep.SERVICE_DISCOVERY,
            "descobrir qual serviço do catálogo melhor atende o cliente",
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
            Set.of(ConversationalAiAction.REQUEST_HUMAN_HANDOFF),
            StepFallbackBehavior.REPEAT_SERVICE_DISCOVERY_WITH_OPTIONS,
            3,
            StructuredEscapeType.SERVICE_DISCOVERY_OPTIONS,
            false
        ));

        contracts.put(ConversationStep.AWAITING_CONFIRMATION, new StepContract(
            ConversationStep.AWAITING_CONFIRMATION,
            "confirmar com o cliente que o serviço sugerido é o correto",
            List.of(new SlotRequirement(ConversationSlotName.CONFIRMED_SERVICE, ConversationSlotLevel.CONFIRMED)),
            List.of(),
            Set.of(
                ConversationalAiAction.CONFIRM_UNDERSTANDING,
                ConversationalAiAction.OFFER_STRUCTURED_OPTIONS,
                ConversationalAiAction.REPEAT_WITH_REFRAME,
                ConversationalAiAction.ACKNOWLEDGE_AND_ADVANCE
            ),
            Set.of(
                ConversationalAiAction.PROPOSE_SERVICE,
                ConversationalAiAction.ASK_CLARIFYING_QUESTION
            ),
            StepFallbackBehavior.REPEAT_CONFIRMATION,
            2,
            StructuredEscapeType.CONFIRMATION_OPTIONS,
            true
        ));

        contracts.put(ConversationStep.AWAITING_TERMS, new StepContract(
            ConversationStep.AWAITING_TERMS,
            "obter aceite explícito dos termos de uso",
            List.of(new SlotRequirement(ConversationSlotName.TERMS_ACCEPTED, ConversationSlotLevel.CONFIRMED)),
            List.of(),
            Set.of(
                ConversationalAiAction.CONFIRM_UNDERSTANDING,
                ConversationalAiAction.REPEAT_WITH_REFRAME,
                ConversationalAiAction.ACKNOWLEDGE_AND_ADVANCE
            ),
            Set.of(
                ConversationalAiAction.PROPOSE_SERVICE,
                ConversationalAiAction.ASK_CLARIFYING_QUESTION,
                ConversationalAiAction.OFFER_STRUCTURED_OPTIONS
            ),
            StepFallbackBehavior.REPEAT_TERMS,
            3,
            StructuredEscapeType.TERMS_RETRY,
            true
        ));

        contracts.put(ConversationStep.AWAITING_PAYMENT_METHOD, new StepContract(
            ConversationStep.AWAITING_PAYMENT_METHOD,
            "coletar a forma de pagamento escolhida pelo cliente",
            List.of(new SlotRequirement(ConversationSlotName.PAYMENT_METHOD, ConversationSlotLevel.CONFIRMED)),
            List.of(),
            Set.of(
                ConversationalAiAction.CONFIRM_UNDERSTANDING,
                ConversationalAiAction.OFFER_STRUCTURED_OPTIONS,
                ConversationalAiAction.REPEAT_WITH_REFRAME,
                ConversationalAiAction.ACKNOWLEDGE_AND_ADVANCE
            ),
            Set.of(
                ConversationalAiAction.PROPOSE_SERVICE,
                ConversationalAiAction.ASK_CLARIFYING_QUESTION
            ),
            StepFallbackBehavior.REPEAT_PAYMENT_OPTIONS,
            2,
            StructuredEscapeType.PAYMENT_OPTIONS,
            true
        ));
    }

    public Optional<StepContract> findByStep(ConversationStep step) {
        return Optional.ofNullable(contracts.get(step));
    }
}
