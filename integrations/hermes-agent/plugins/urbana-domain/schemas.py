"""Input schemas for the only toolset exposed by the Urba profile.

The plugin deliberately models only business arguments. Session identity is
added by the Hermes runtime and technical identifiers are resolved by Urbana
Connect, never by the model.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping


def _string(payload: Mapping[str, Any], key: str, *, required: bool = True) -> str | None:
    value = payload.get(key)
    if value is None and not required:
        return None
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{key} must be a non-empty string")
    return value.strip()


@dataclass(frozen=True)
class GetCustomerProfileInput:
    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "GetCustomerProfileInput":
        _reject_identifiers(payload)
        return cls()


@dataclass(frozen=True)
class UpdateCustomerFactInput:
    fact_type: str
    value: Any
    evidence: str
    confidence: str

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "UpdateCustomerFactInput":
        _reject_identifiers(payload)
        if "value" not in payload:
            raise ValueError("value is required")
        confidence = _string(payload, "confidence")
        if confidence not in {"CONFIRMED", "TENTATIVE"}:
            raise ValueError("confidence must be CONFIRMED or TENTATIVE")
        return cls(
            _string(payload, "factType"),
            payload["value"],
            _string(payload, "evidence"),
            confidence,
        )


@dataclass(frozen=True)
class ListAvailableServicesInput:
    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ListAvailableServicesInput":
        _reject_identifiers(payload)
        return cls()


@dataclass(frozen=True)
class PrepareTermsInput:
    service_type: str

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PrepareTermsInput":
        _reject_identifiers(payload)
        return cls(_string(payload, "serviceType"))


@dataclass(frozen=True)
class PreparePaymentInput:
    service_type: str
    method: str

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PreparePaymentInput":
        _reject_identifiers(payload)
        return cls(_string(payload, "serviceType"), _string(payload, "method"))


@dataclass(frozen=True)
class RequestHumanHandoffInput:
    reason: str

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RequestHumanHandoffInput":
        _reject_identifiers(payload)
        return cls(_string(payload, "reason"))


def _reject_identifiers(payload: Mapping[str, Any]) -> None:
    forbidden = {"contactId", "turnId", "idempotencyKey", "phoneNumber"}
    supplied = forbidden.intersection(payload.keys())
    if supplied:
        raise ValueError("model identifiers are not accepted: " + ", ".join(sorted(supplied)))
