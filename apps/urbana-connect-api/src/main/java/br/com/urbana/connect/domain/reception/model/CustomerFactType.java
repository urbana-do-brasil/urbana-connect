package br.com.urbana.connect.domain.reception.model;

import java.util.Arrays;
import java.util.List;

public enum CustomerFactType {
    PRONOUN_PREFERENCE,
    FIRST_TIME_HIRING,
    OCCUPATION,
    NEED,
    SELECTED_SERVICE,
    /** Explicit environment binding; operational metadata, never an ICP field. */
    ENVIRONMENT;

    private static final List<String> ICP_FIELD_NAMES = List.of(
            PRONOUN_PREFERENCE.name(), FIRST_TIME_HIRING.name(), OCCUPATION.name());

    public static boolean isAllowed(String value) {
        return value != null && Arrays.stream(values()).anyMatch(v -> v.name().equalsIgnoreCase(value));
    }

    public boolean isIcpField() {
        return this == PRONOUN_PREFERENCE || this == FIRST_TIME_HIRING || this == OCCUPATION;
    }

    public static List<String> icpFieldNames() {
        return ICP_FIELD_NAMES;
    }

    public static boolean isIcpField(String value) {
        return value != null && ICP_FIELD_NAMES.stream().anyMatch(name -> name.equalsIgnoreCase(value));
    }
}
