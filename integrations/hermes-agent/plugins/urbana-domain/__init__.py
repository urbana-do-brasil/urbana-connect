"""Restricted Urbana domain plugin for the isolated Hermes POC.

Hermes v2026.8.3 discovers directory plugins through ``register(ctx)``.  The
plugin does not expose a generic HTTP or shell tool: every handler validates
business-only arguments and derives the session identity from Hermes' runtime.
"""

try:
    from .tools import TOOL_NAMES, dispatch_tool
except ImportError:  # direct local unittest import
    from tools import TOOL_NAMES, dispatch_tool

TOOLSET = "urbana-domain"

TOOL_DESCRIPTIONS = {
    "get_customer_profile": (
        "Consulta o perfil e os fatos já confirmados do cliente para manter contexto. "
        "Não use para inventar ou inferir dados ausentes."
    ),
    "update_customer_fact": (
        "Registra um fato que o cliente acabou de informar, usando somente os tipos permitidos."
    ),
    "list_available_services": (
        "Consulta o catálogo aprovado de serviços. Use quando o cliente perguntar como funciona, "
        "o que recebe, etapas, escopo, limites, disponibilidade ou preço. O resultado contém o "
        "catálogo rico; responda à dúvida sem iniciar termos, pagamento ou handoff por esse motivo. "
        "Ao detalhar um serviço identificado, explicite que é uma consultoria online e mencione "
        "processo, Manual em PDF, Tour Virtual, 3 opções e até 2 rodadas de ajustes."
    ),
    "prepare_terms": (
        "Prepara os termos de um serviço já escolhido para uma intenção clara de contratação. "
        "Use somente nesse momento; não use para dúvidas, explicações ou comparação de serviços."
    ),
    "prepare_payment": (
        "Prepara o pagamento de um serviço somente depois de os termos terem sido apresentados "
        "e aceitos de forma textual clara e de a pessoa escolher uma forma válida. "
        "As formas aceitas são PIX ou CARD (cartão de crédito). Se a forma não tiver sido "
        "informada, não use a ferramenta: pergunte se a pessoa prefere PIX ou cartão de crédito. "
        "Nunca use `link` como método; o link é a instrução retornada após o preparo. "
        "Não repita a chamada com os mesmos argumentos depois de uma rejeição: corrija a informação "
        "ou faça a pergunta necessária ao cliente."
    ),
    "request_human_handoff": (
        "Transfere para atendimento humano quando o cliente pedir uma pessoa explicitamente ou "
        "quando houver incapacidade real de avançar com as informações disponíveis."
    ),
}


def _schema(tool_name: str) -> dict:
    parameters = {"type": "object", "additionalProperties": False, "properties": {}}
    if tool_name in {"get_customer_profile", "list_available_services"}:
        return _function_schema(tool_name, parameters)
    if tool_name == "update_customer_fact":
        parameters["properties"] = {
            "factType": {"type": "string", "enum": [
                "PRONOUN_PREFERENCE", "FIRST_TIME_HIRING", "OCCUPATION", "NEED", "SELECTED_SERVICE"
            ]},
            "value": {},
            "evidence": {"type": "string"},
            "confidence": {"type": "string", "enum": ["CONFIRMED", "TENTATIVE"]},
        }
        parameters["required"] = ["factType", "value", "evidence", "confidence"]
    elif tool_name == "prepare_terms":
        parameters["properties"] = {"serviceType": {"type": "string"}}
        parameters["required"] = ["serviceType"]
    elif tool_name == "prepare_payment":
        parameters["properties"] = {
            "serviceType": {"type": "string"},
            "method": {
                "type": "string",
                "enum": ["PIX", "CARD"],
                "description": "Forma válida: PIX ou cartão de crédito.",
            },
        }
        parameters["required"] = ["serviceType", "method"]
    elif tool_name == "request_human_handoff":
        parameters["properties"] = {"reason": {"type": "string"}}
        parameters["required"] = ["reason"]
    return _function_schema(tool_name, parameters)


def _function_schema(tool_name: str, parameters: dict) -> dict:
    return {
        "name": tool_name,
        "description": TOOL_DESCRIPTIONS[tool_name],
        "parameters": parameters,
    }


def register(ctx) -> None:
    """Register exactly the six typed Urbana tools with Hermes' registry."""
    for name in TOOL_NAMES:
        def handler(arguments, _name=name, **runtime):
            return dispatch_tool(_name, arguments, runtime)

        ctx.register_tool(
            name=name,
            toolset=TOOLSET,
            schema=_schema(name),
            handler=handler,
            description=TOOL_DESCRIPTIONS[name],
        )


__all__ = ["TOOL_NAMES", "TOOLSET", "dispatch_tool", "register"]
