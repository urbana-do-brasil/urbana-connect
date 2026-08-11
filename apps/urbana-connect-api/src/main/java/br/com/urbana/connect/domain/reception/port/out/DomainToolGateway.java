package br.com.urbana.connect.domain.reception.port.out;

import br.com.urbana.connect.domain.reception.model.DomainToolName;

import java.util.Map;

/** Typed domain operation boundary used by the restricted tool controller. */
public interface DomainToolGateway {
    Map<String, Object> execute(DomainToolName toolName, String contactId, Map<String, Object> arguments);
}
