package br.com.urbana.connect.domain.conversation.model;

public enum StepFallbackBehavior {
    REPEAT_GREETING_WITH_BUTTONS,
    REPEAT_ICP_WITH_REFRAME,
    REPEAT_SERVICE_DISCOVERY_WITH_OPTIONS,
    REPEAT_CONFIRMATION,
    REPEAT_TERMS,
    REPEAT_PAYMENT_OPTIONS,
    GENERIC_SAFE_FALLBACK
}
