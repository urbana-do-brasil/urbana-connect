package br.com.urbana.connect.application.reception.tools;

import br.com.urbana.connect.domain.reception.model.DomainToolName;

import java.util.Map;

/** Use-case boundary for typed Urbana domain operations. */
public interface DomainToolService {
    Map<String, Object> execute(DomainToolName toolName, String contactId, Map<String, Object> arguments);

    /**
     * Context-aware overload used by production adapters. The legacy default
     * keeps the small lambda/fake contract useful in unit tests.
     */
    default Map<String, Object> execute(DomainToolName toolName, String contactId,
                                         Map<String, Object> arguments, ToolExecutionContext context) {
        return execute(toolName, contactId, arguments);
    }
}
