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


class PluginToolError(ValueError):
    """Expected validation or domain rejection returned as JSON."""


@dataclass(frozen=True)
class PluginClient:
    base_url: str
    internal_token: str
    opener: Callable[..., Any] | None = None

    def call(self, session_id: str, principal: str, tool_name: str, arguments: Mapping[str, Any]) -> Any:
        if tool_name not in TOOL_NAMES:
            raise PluginToolError(f"tool is not allowlisted: {tool_name}")
        if not session_id or not isinstance(session_id, str):
            raise PluginToolError("runtime session id is required")
        if not principal or not isinstance(principal, str):
            raise PluginToolError("technical principal is required")
        if FORBIDDEN_IDENTIFIERS.intersection(arguments):
            raise PluginToolError("model identifiers are not accepted")
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
        except (error.URLError, TimeoutError, ValueError) as exc:
            raise PluginToolError(f"domain tool request failed: {exc}") from exc
        if not isinstance(payload, dict):
            raise PluginToolError("domain tool returned a non-object JSON response")
        if payload.get("ok") is False:
            raise PluginToolError(str(payload.get("error") or "domain operation rejected"))
        return payload.get("result", payload)


def _runtime_identity(runtime: Mapping[str, Any]) -> tuple[str, str]:
    # Hermes v2026.8.3 passes task_id=session_id to project plugins.
    session_id = runtime.get("task_id") or runtime.get("session_id")
    # Principal is a code/configured allowlist value, never model-controlled
    # or copied from arbitrary runtime metadata.
    principal = TECHNICAL_PRINCIPAL
    if not isinstance(session_id, str) or not session_id.strip():
        raise PluginToolError("runtime task_id/session_id is required")
    if not isinstance(principal, str) or not principal.strip():
        raise PluginToolError("technical principal is required")
    return session_id.strip(), principal.strip()


def _validate(tool_name: str, arguments: Mapping[str, Any]) -> None:
    if not isinstance(arguments, Mapping):
        raise PluginToolError("arguments must be an object")
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
        raise PluginToolError(f"tool is not allowlisted: {tool_name}")


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
    except Exception as exc:  # plugin boundary must never leak an exception to Hermes
        return json.dumps({"ok": False, "error": str(exc)}, ensure_ascii=False, separators=(",", ":"))


def handle_tool_call(tool_name: str, arguments: Mapping[str, Any] | None = None,
                     runtime: Mapping[str, Any] | None = None, client: PluginClient | None = None) -> str:
    return dispatch_tool(tool_name, arguments, runtime, client)
