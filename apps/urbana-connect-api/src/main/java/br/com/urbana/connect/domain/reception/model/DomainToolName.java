package br.com.urbana.connect.domain.reception.model;

import java.util.Arrays;

public enum DomainToolName {
    GET_CUSTOMER_PROFILE("get_customer_profile"),
    UPDATE_CUSTOMER_FACT("update_customer_fact"),
    LIST_AVAILABLE_SERVICES("list_available_services"),
    PREPARE_TERMS("prepare_terms"),
    PREPARE_PAYMENT("prepare_payment"),
    REQUEST_HUMAN_HANDOFF("request_human_handoff");

    private final String wireName;

    DomainToolName(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static DomainToolName fromWireName(String name) {
        return Arrays.stream(values()).filter(v -> v.wireName.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tool is not allowlisted: " + name));
    }
}
