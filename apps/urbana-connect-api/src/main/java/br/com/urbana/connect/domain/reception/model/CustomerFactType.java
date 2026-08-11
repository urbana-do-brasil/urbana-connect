package br.com.urbana.connect.domain.reception.model;

import java.util.Arrays;

public enum CustomerFactType {
    PRONOUN_PREFERENCE,
    FIRST_TIME_HIRING,
    OCCUPATION,
    NEED,
    SELECTED_SERVICE;

    public static boolean isAllowed(String value) {
        return value != null && Arrays.stream(values()).anyMatch(v -> v.name().equalsIgnoreCase(value));
    }
}
