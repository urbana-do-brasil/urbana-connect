"""Hermes project-plugin handlers for the Urbana domain boundary.

The runtime supplies ``task_id`` (the persistent Hermes session id). The model
can supply only business arguments; the backend resolves contact, turn,
source-message and idempotency from the active lease.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Any, Callable, Mapping
from urllib import error, request

try:  # package import during Hermes plugin discovery
    from .schemas import (
        GetCustomerProfileInput,
        ListAvailableServicesInput,
        PreparePaymentInput,
        PrepareTermsInput,
        RequestHumanHandoffInput,
        UpdateCustomerFactInput,
    )
except ImportError:  # direct import used by the local unittest command
    from schemas import (
        GetCustomerProfileInput,
        ListAvailableServicesInput,
        PreparePaymentInput,
        PrepareTermsInput,
        RequestHumanHandoffInput,
        UpdateCustomerFactInput,
    )

TOOL_NAMES = (
    "get_customer_profile",
    "update_customer_fact",
    "list_available_services",
    "prepare_terms",
    "prepare_payment",
    "request_human_handoff",
)
TECHNICAL_PRINCIPAL = "hermes-urbana-domain"
FORBIDDEN_IDENTIFIERS = frozenset({"contactId", "turnId", "idempotencyKey", "phoneNumber"})


SAFE_FAILURE = {
    "code": "TEMPORARILY_UNAVAILABLE",
    "nextAction": "WAIT_OR_HANDOFF",
    "missingFields": [],
    "customerMessage": "Não consegui concluir esta etapa agora. Posso tentar novamente ou chamar a arquiteta.",
}
SAFE_ERROR_FIELDS = frozenset({"code", "nextAction", "missingFields", "customerMessage"})


def _is_structured_business_rejection(payload: Any) -> bool:
    if not isinstance(payload, Mapping) or payload.get("ok") is not False:
        return False
    safe_error = payload.get("error")
    if not isinstance(safe_error, Mapping) or set(safe_error) != SAFE_ERROR_FIELDS:
        return False

    code = safe_error["code"]
    next_action = safe_error["nextAction"]
    missing_fields = safe_error["missingFields"]
    customer_message = safe_error["customerMessage"]
    return (
        isinstance(code, str) and bool(code.strip())
        and isinstance(next_action, str) and bool(next_action.strip())
        and isinstance(missing_fields, list)
        and all(isinstance(field, str) and bool(field.strip()) for field in missing_fields)
        and isinstance(customer_message, str) and bool(customer_message.strip())
    )


class PluginToolError(ValueError):
    """Safe validation or domain rejection returned as structured JSON."""

    def __init__(self, safe_error: Mapping[str, Any] | None = None) -> None:
        candidate = safe_error if isinstance(safe_error, Mapping) else {}
        self.safe_error = {
            "code": str(candidate.get("code") or "INVALID_REQUEST"),
            "nextAction": str(candidate.get("nextAction") or "CORRECT_INPUT"),
            "missingFields": list(candidate.get("missingFields") or []),
            "customerMessage": str(candidate.get("customerMessage") or
                                   "Preciso de dados válidos para continuar."),
        }
        super().__init__(self.safe_error["customerMessage"])


@dataclass(frozen=True)
class PluginClient:
    base_url: str
    internal_token: str
    opener: Callable[..., Any] | None = None

    def call(self, session_id: str, principal: str, tool_name: str, arguments: Mapping[str, Any]) -> Any:
        if tool_name not in TOOL_NAMES:
            raise PluginToolError()
        if not session_id or not isinstance(session_id, str):
            raise PluginToolError()
        if not principal or not isinstance(principal, str):
            raise PluginToolError()
        if FORBIDDEN_IDENTIFIERS.intersection(arguments):
            raise PluginToolError()
        body = json.dumps({
            "sessionId": session_id,
            "principal": principal,
            "arguments": dict(arguments),
        }).encode("utf-8")
        endpoint = self.base_url.rstrip("/") + f"/internal/poc/domain-tools/{tool_name}"
        req = request.Request(endpoint, data=body, method="POST", headers={
            "Authorization": f"Bearer {self.internal_token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        })
        opener = self.opener or request.urlopen
        try:
            with opener(req, timeout=15) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            # Business rejections use HTTP 409 at the internal boundary. Keep
            # their safe, structured envelope so Hermes can correct the turn
            # instead of seeing a generic transport outage.
            if exc.code != 409:
                raise PluginToolError(SAFE_FAILURE) from exc
            try:
                payload = json.loads(exc.read().decode("utf-8"))
            except (AttributeError, OSError, TypeError, UnicodeDecodeError, ValueError) as decode_error:
                raise PluginToolError(SAFE_FAILURE) from decode_error
            if not _is_structured_business_rejection(payload):
                raise PluginToolError(SAFE_FAILURE)
            raise PluginToolError(payload["error"])
        except (error.URLError, TimeoutError, ValueError) as exc:
            raise PluginToolError(SAFE_FAILURE) from exc
        if not isinstance(payload, dict):
            raise PluginToolError(SAFE_FAILURE)
        if payload.get("ok") is False:
            raise PluginToolError(payload.get("error"))
        return payload.get("result", payload)


def _runtime_identity(runtime: Mapping[str, Any]) -> tuple[str, str]:
    # Hermes v2026.8.3 passes task_id=session_id to project plugins.
    session_id = runtime.get("task_id") or runtime.get("session_id")
    # Principal is a code/configured allowlist value, never model-controlled
    # or copied from arbitrary runtime metadata.
    principal = TECHNICAL_PRINCIPAL
    if not isinstance(session_id, str) or not session_id.strip():
        raise PluginToolError()
    if not isinstance(principal, str) or not principal.strip():
        raise PluginToolError()
    return session_id.strip(), principal.strip()


def _validate(tool_name: str, arguments: Mapping[str, Any]) -> None:
    if not isinstance(arguments, Mapping):
        raise PluginToolError()
    if tool_name == "get_customer_profile":
        GetCustomerProfileInput.from_payload(arguments)
    elif tool_name == "update_customer_fact":
        UpdateCustomerFactInput.from_payload(arguments)
    elif tool_name == "list_available_services":
        ListAvailableServicesInput.from_payload(arguments)
    elif tool_name == "prepare_terms":
        PrepareTermsInput.from_payload(arguments)
    elif tool_name == "prepare_payment":
        PreparePaymentInput.from_payload(arguments)
    elif tool_name == "request_human_handoff":
        RequestHumanHandoffInput.from_payload(arguments)
    else:
        raise PluginToolError()


def dispatch_tool(tool_name: str, arguments: Mapping[str, Any] | None = None,
                  runtime: Mapping[str, Any] | None = None, client: PluginClient | None = None) -> str:
    """Dispatch one tool and *always* return a JSON object string."""
    try:
        arguments = {} if arguments is None else arguments
        runtime = {} if runtime is None else runtime
        _validate(tool_name, arguments)
        session_id, principal = _runtime_identity(runtime)
        active_client = client or PluginClient(
            os.getenv("URBANA_CONNECT_INTERNAL_URL", "http://127.0.0.1:8081"),
            os.getenv("HERMES_INTERNAL_TOOL_TOKEN", ""),
        )
        result = active_client.call(session_id, principal, tool_name, arguments)
        return json.dumps({"ok": True, "result": result}, ensure_ascii=False, separators=(",", ":"))
    except PluginToolError as exc:
        return json.dumps({"ok": False, "error": exc.safe_error}, ensure_ascii=False, separators=(",", ":"))
    except Exception:  # plugin boundary must never leak an exception to Hermes
        return json.dumps({"ok": False, "error": SAFE_FAILURE}, ensure_ascii=False, separators=(",", ":"))


def handle_tool_call(tool_name: str, arguments: Mapping[str, Any] | None = None,
                     runtime: Mapping[str, Any] | None = None, client: PluginClient | None = None) -> str:
    return dispatch_tool(tool_name, arguments, runtime, client)
