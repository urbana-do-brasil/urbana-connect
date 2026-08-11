package br.com.urbana.connect.application.reception.tools;

import br.com.urbana.connect.domain.reception.model.DomainToolName;

import java.util.List;
import java.util.Map;

/** Small fixture-backed implementation used until the commercial use cases are wired in US1. */
public class DefaultDomainToolService implements DomainToolService {
    private static final List<Map<String, Object>> SERVICES = List.of(
            Map.of("serviceType", "DECOR", "description", "Projeto de decoração", "price", "fixture:R$ 0"),
            Map.of("serviceType", "ARCHITECTURE", "description", "Projeto de arquitetura", "price", "fixture:R$ 0")
    );

    @Override
    public Map<String, Object> execute(DomainToolName toolName, String contactId, Map<String, Object> arguments) {
        return switch (toolName) {
            case GET_CUSTOMER_PROFILE -> Map.of("facts", List.of(), "missingIcpFields",
                    List.of("PRONOUN_PREFERENCE", "FIRST_TIME_HIRING", "OCCUPATION"), "previousServices", List.of());
            case LIST_AVAILABLE_SERVICES -> Map.of("services", SERVICES);
            case UPDATE_CUSTOMER_FACT -> Map.of("status", "RECORDED", "factType", arguments.get("factType"));
            case PREPARE_TERMS -> Map.of("status", "PRESENTED", "serviceType", arguments.get("serviceType"),
                    "url", "https://fixtures.urbana.local/terms");
            case PREPARE_PAYMENT -> Map.of("status", "PREPARED", "serviceType", arguments.get("serviceType"),
                    "instruction", "fixture: pagamento não transacional");
            case REQUEST_HUMAN_HANDOFF -> Map.of("status", "HUMAN_MODE", "reason", arguments.get("reason"));
        };
    }
}
