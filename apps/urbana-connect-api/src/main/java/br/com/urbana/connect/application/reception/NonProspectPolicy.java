package br.com.urbana.connect.application.reception;

import br.com.urbana.connect.domain.reception.model.AgentNextAction;
import br.com.urbana.connect.domain.reception.model.AgentOutput;
import br.com.urbana.connect.domain.reception.model.CustomerFact;
import br.com.urbana.connect.domain.reception.model.ReceptionConversation;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * Read-only conversational boundary for contacts with no confirmed
 * commercial intent.
 *
 * <p>This policy deliberately does not create facts, select a service, or
 * change commercial state. It only returns a publication-safe decision and an
 * immutable next state for the single light context probe allowed by US4.</p>
 */
public final class NonProspectPolicy {
    private static final int MAX_LIGHT_PROBES = 1;

    private static final String IDENTITY_MESSAGE =
            "Você está falando com a Urba, assistente virtual da Urbana do Brasil.";
    private static final String LIGHT_PROBE_MESSAGE =
            "Entendi. Qual é, brevemente, o assunto em que você precisa de ajuda?";
    private static final String WRONG_CONTACT_MESSAGE =
            "Sem problema — parece que este contato foi um engano. Vou encerrar por aqui. Obrigada.";
    private static final String UNRELATED_MESSAGE =
            "Entendi. Como esse assunto não tem relação com a Urbana, vou encerrar por aqui. "
                    + "Obrigada pelo contato.";
    private static final String LIMIT_MESSAGE =
            "Entendi. Como não há uma necessidade comercial clara, vou encerrar por aqui. "
                    + "Obrigada pelo contato.";
    private static final String INSTITUTIONAL_MESSAGE =
            "Não consigo confirmar esse pedido institucional por aqui. Posso encaminhar você para "
                    + "um atendimento humano, sem compromisso comercial.";
    private static final String INSTITUTIONAL_HANDOFF_REASON =
            "pedido institucional não confirmado";

    public Decision decide(String message) {
        return decide(message, State.initial());
    }

    /**
     * Identifies the explicit non-prospect language that may be handled
     * locally, before an agent session is resolved. Generic commercial text
     * deliberately does not match this boundary.
     */
    public boolean isApplicable(String message) {
        String normalized = normalize(message);
        return isWrongContact(normalized)
                || isIdentityQuestion(normalized)
                || isInstitutionalRequest(normalized)
                || isUnrelatedSubject(normalized)
                || isGeneralNonProspectQuestion(normalized);
    }

    public Decision decide(String message, State state) {
        State currentState = state == null ? State.initial() : state;
        String normalized = normalize(message);

        if (isWrongContact(normalized)) {
            return close(Disposition.CLOSE, WRONG_CONTACT_MESSAGE, currentState);
        }
        if (isIdentityQuestion(normalized)) {
            return decision(Disposition.IDENTIFY, new AgentOutput(IDENTITY_MESSAGE,
                    AgentNextAction.AWAIT_CUSTOMER), currentState);
        }
        if (isInstitutionalRequest(normalized)) {
            return decision(Disposition.OFFER_HUMAN,
                    new AgentOutput(INSTITUTIONAL_MESSAGE, AgentNextAction.HANDOFF,
                            INSTITUTIONAL_HANDOFF_REASON), currentState);
        }
        if (isUnrelatedSubject(normalized)) {
            return close(Disposition.CLOSE, UNRELATED_MESSAGE, currentState);
        }
        if (currentState.lightProbesUsed() >= MAX_LIGHT_PROBES) {
            return close(Disposition.CLOSE, LIMIT_MESSAGE, currentState);
        }

        State nextState = new State(currentState.lightProbesUsed() + 1);
        return decision(Disposition.LIGHT_PROBE,
                new AgentOutput(LIGHT_PROBE_MESSAGE, AgentNextAction.AWAIT_CUSTOMER), nextState);
    }

    /**
     * Evaluates against authoritative snapshots without taking ownership of
     * either one. The snapshots are intentionally not mutated or persisted by
     * this policy.
     */
    public Decision decide(String message, State state, Collection<CustomerFact> facts,
                           ReceptionConversation conversation) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(conversation, "conversation");
        return decide(message, state);
    }

    private Decision close(Disposition disposition, String message, State state) {
        return decision(disposition, new AgentOutput(message, AgentNextAction.NONE), state);
    }

    private Decision decision(Disposition disposition, AgentOutput output, State nextState) {
        return new Decision(disposition, CommercialDecision.DO_NOT_INFER_PURCHASE, output,
                nextState, false, false);
    }

    private boolean isIdentityQuestion(String message) {
        return containsAny(message,
                "quem esta respondendo", "quem responde", "quem fala", "com quem falo",
                "quem e voce", "qual empresa responde");
    }

    private boolean isWrongContact(String message) {
        return containsAny(message,
                "numero errado", "contato errado", "pessoa errada", "mensagem errada",
                "foi engano", "era engano", "desculpa engano", "nao sou essa pessoa");
    }

    private boolean isInstitutionalRequest(String message) {
        return containsAny(message,
                "pedido institucional", "assunto institucional", "parceria institucional",
                "parceria", "imprensa", "fornecedor", "orgao publico", "prefeitura");
    }

    private boolean isUnrelatedSubject(String message) {
        return containsAny(message,
                "nao tem relacao com", "sem relacao com", "assunto sem relacao",
                "assunto nao relacionado", "nao e sobre a urbana");
    }

    private boolean isGeneralNonProspectQuestion(String message) {
        if (!containsAny(message, "duvida geral", "duvida", "canal certo", "posso explicar",
                "apenas esclarecer", "so esclarecer", "assunto")) {
            return false;
        }
        return !containsAny(message, "contratar", "contratacao", "comprar", "decorar", "pintar",
                "reforma", "servico", "preco", "valor", "pagamento", "pix", "cartao");
    }

    private boolean containsAny(String message, String... terms) {
        for (String term : terms) {
            if (containsTerm(message, term)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTerm(String message, String term) {
        if (message.isBlank()) {
            return false;
        }
        String normalizedTerm = normalize(term);
        int fromIndex = 0;
        while (fromIndex < message.length()) {
            int start = message.indexOf(normalizedTerm, fromIndex);
            if (start < 0) {
                return false;
            }
            int end = start + normalizedTerm.length();
            boolean startsAtBoundary = start == 0 || !isTermCharacter(message.charAt(start - 1));
            boolean endsAtBoundary = end == message.length() || !isTermCharacter(message.charAt(end));
            if (startsAtBoundary && endsAtBoundary) {
                return true;
            }
            fromIndex = end;
        }
        return false;
    }

    private static boolean isTermCharacter(char character) {
        return Character.isLetterOrDigit(character);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    public enum Disposition {
        IDENTIFY,
        LIGHT_PROBE,
        CLOSE,
        OFFER_HUMAN
    }

    /** Explicitly records that no purchase intent was inferred. */
    public enum CommercialDecision {
        DO_NOT_INFER_PURCHASE
    }

    public record State(int lightProbesUsed) {
        public State {
            if (lightProbesUsed < 0) {
                throw new IllegalArgumentException("lightProbesUsed must not be negative");
            }
        }

        public static State initial() {
            return new State(0);
        }
    }

    public record Decision(Disposition disposition,
                           CommercialDecision commercialDecision,
                           AgentOutput output,
                           State nextState,
                           boolean shouldCollectIcp,
                           boolean shouldProgressCommercialFlow) {
        public Decision {
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(commercialDecision, "commercialDecision");
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(nextState, "nextState");
            if (shouldCollectIcp || shouldProgressCommercialFlow) {
                throw new IllegalArgumentException(
                        "non-prospect decisions cannot collect ICP or progress commercial flow");
            }
        }
    }
}
