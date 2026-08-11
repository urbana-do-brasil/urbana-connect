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
        parameters["properties"] = {"serviceType": {"type": "string"}, "method": {"type": "string"}}
        parameters["required"] = ["serviceType", "method"]
    elif tool_name == "request_human_handoff":
        parameters["properties"] = {"reason": {"type": "string"}}
        parameters["required"] = ["reason"]
    return _function_schema(tool_name, parameters)


def _function_schema(tool_name: str, parameters: dict) -> dict:
    return {
        "name": tool_name,
        "description": f"Urbana domain operation: {tool_name}",
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
            description=f"Urbana domain operation: {name}",
        )


__all__ = ["TOOL_NAMES", "TOOLSET", "dispatch_tool", "register"]
