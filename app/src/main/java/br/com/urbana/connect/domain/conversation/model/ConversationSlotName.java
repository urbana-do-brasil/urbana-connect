package br.com.urbana.connect.domain.conversation.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ConversationSlotName {
    NEEDS_DISCOVERY_HELP("needsDiscoveryHelp"),
    PRONOUN_PREFERENCE("pronounPreference"),
    FIRST_TIME_HIRING_DESIGNER("firstTimeHiringDesigner"),
    OCCUPATION("occupation"),
    SUGGESTED_SERVICE("suggestedService"),
    CONFIRMED_SERVICE("confirmedService"),
    TERMS_ACCEPTED("termsAccepted"),
    PAYMENT_METHOD("paymentMethod");

    private final String key;

    ConversationSlotName(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    @JsonCreator
    public static ConversationSlotName fromKey(String key) {
        return Arrays.stream(values())
            .filter(value -> value.key.equalsIgnoreCase(key))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown conversation slot: " + key));
    }
}
