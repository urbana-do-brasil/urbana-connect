import json
import unittest
from io import BytesIO
from pathlib import Path
from urllib import error

import __init__ as plugin
from tools import SAFE_FAILURE, PluginClient, TOOL_NAMES, dispatch_tool


class FakeResponse:
    def __init__(self, body):
        self.body = body

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def read(self):
        return json.dumps(self.body).encode("utf-8")


class PluginToolsTest(unittest.TestCase):
    def test_official_register_exposes_only_the_six_domain_tools(self):
        registered = []

        class Context:
            def register_tool(self, **kwargs):
                registered.append(kwargs)

        plugin.register(Context())

        self.assertEqual([item["name"] for item in registered], list(TOOL_NAMES))
        self.assertTrue(all(item["toolset"] == "urbana-domain" for item in registered))
        self.assertTrue(all(item["schema"]["parameters"]["additionalProperties"] is False
                            for item in registered))
        update_schema = next(item["schema"] for item in registered
                             if item["name"] == "update_customer_fact")
        self.assertEqual(update_schema["parameters"]["required"],
                         ["factType", "value", "evidence", "confidence"])
        self.assertIn("ENVIRONMENT", update_schema["parameters"]["properties"]["factType"]["enum"])

    def test_surface_contains_only_six_domain_tools(self):
        self.assertEqual(len(TOOL_NAMES), 6)
        self.assertEqual(set(TOOL_NAMES), {
            "get_customer_profile", "update_customer_fact", "list_available_services",
            "prepare_terms", "prepare_payment", "request_human_handoff",
        })

    def test_tool_descriptions_make_informative_service_lookup_distinct_from_commercial_actions(self):
        registered = []

        class Context:
            def register_tool(self, **kwargs):
                registered.append(kwargs)

        plugin.register(Context())
        descriptions = {item["name"]: item["description"] for item in registered}

        self.assertIn("como funciona", descriptions["list_available_services"].lower())
        self.assertIn("catálogo", descriptions["list_available_services"].lower())
        self.assertIn("intenção clara", descriptions["prepare_terms"].lower())
        self.assertIn("não use para dúvidas", descriptions["prepare_terms"].lower())
        self.assertIn("aceitos", descriptions["prepare_payment"].lower())
        self.assertIn("1 serviço por ambiente", descriptions["prepare_payment"].lower())
        self.assertIn("simulação", descriptions["prepare_payment"].lower())

    def test_prepare_payment_schema_enumerates_canonical_methods_with_natural_labels(self):
        registered = []

        class Context:
            def register_tool(self, **kwargs):
                registered.append(kwargs)

        plugin.register(Context())
        prepare_payment = next(item["schema"] for item in registered
                               if item["name"] == "prepare_payment")
        method = prepare_payment["parameters"]["properties"]["method"]

        self.assertEqual(method["enum"], ["PIX", "CARD"])
        self.assertIn("PIX", method["description"])
        self.assertIn("cartão de crédito", method["description"].lower())

    def test_soul_requires_progressive_replies_and_safe_payment_method_question(self):
        soul = (Path(__file__).resolve().parents[2] / "profile" / "SOUL.md").read_text()
        normalized = soul.lower()
        collapsed = " ".join(normalized.split())

        self.assertIn("responda somente à nova dúvida ou decisão", normalized)
        self.assertIn("não repita a lista completa", normalized)
        self.assertIn("pix ou cartão de crédito", normalized)
        self.assertIn("não chame `prepare_payment`", normalized)
        self.assertIn("não exponha ao cliente", normalized)
        self.assertIn("manual do espaço", collapsed)
        self.assertIn("tour virtual", collapsed)
        self.assertIn("suporte", collapsed)
        self.assertIn("uma pergunta de perfil por vez", collapsed)
        self.assertNotIn("await_payment_approval", normalized)
        self.assertNotIn("terms_not_accepted", normalized)

    def test_handler_forwards_runtime_session_only(self):
        requests = []

        def opener(req, timeout):
            requests.append(json.loads(req.data.decode("utf-8")))
            return FakeResponse({"ok": True, "result": {"facts": []}})

        result = dispatch_tool("get_customer_profile", {},
                               {"task_id": "session-1", "principal": "test-principal"},
                               PluginClient("http://internal", "token", opener))
        self.assertEqual(json.loads(result)["ok"], True)
        self.assertEqual(requests[0]["sessionId"], "session-1")
        self.assertNotIn("contactId", requests[0])
        self.assertNotIn("turnId", requests[0])

    def test_handler_always_returns_json_and_rejects_model_identifiers(self):
        result = dispatch_tool("prepare_payment", {
            "serviceType": "DECOR", "method": "PIX", "contactId": "chosen-by-model",
        }, {"task_id": "session-1"}, None)
        payload = json.loads(result)
        self.assertFalse(payload["ok"])
        self.assertIsInstance(payload["error"], dict)
        self.assertEqual(payload["error"]["code"], "TEMPORARILY_UNAVAILABLE")

    def test_handler_catches_missing_runtime_session(self):
        payload = json.loads(dispatch_tool("list_available_services", {}, {}, None))
        self.assertFalse(payload["ok"])
        self.assertEqual(payload["error"]["nextAction"], "CORRECT_INPUT")

    def test_transports_structured_business_rejection_without_exception_text(self):
        def opener(_req, timeout):
            return FakeResponse({
                "ok": False,
                "error": {
                    "code": "TERMS_NOT_ACCEPTED",
                    "nextAction": "ASK_FOR_CLEAR_ACCEPTANCE",
                    "missingFields": [],
                    "customerMessage": "Antes do pagamento, preciso do seu aceite claro dos termos.",
                },
            })

        payload = json.loads(dispatch_tool(
            "prepare_payment", {"serviceType": "DECOR", "method": "PIX"},
            {"task_id": "session-1"}, PluginClient("http://internal", "token", opener)))

        self.assertFalse(payload["ok"])
        self.assertEqual(payload["error"]["code"], "TERMS_NOT_ACCEPTED")
        self.assertNotIn("exception", json.dumps(payload).lower())

    def test_transport_failure_never_exposes_forbidden_technical_vocabulary(self):
        def opener(_req, timeout):
            raise RuntimeError("HTTP 500 database API exception; retry with idempotency key")

        payload = json.loads(dispatch_tool(
            "get_customer_profile", {}, {"task_id": "session-1"},
            PluginClient("http://internal", "token", opener)))
        rendered = json.dumps(payload, ensure_ascii=False).lower()

        self.assertFalse(payload["ok"])
        for forbidden in ("http", "database", "api", "exception", "retry", "idempotency", "stack"):
            self.assertNotIn(forbidden, rendered)

    def test_http_business_rejection_keeps_the_structured_customer_safe_error(self):
        body = json.dumps({
            "ok": False,
            "error": {
                "code": "PAYMENT_METHOD_INVALID",
                "nextAction": "ASK_FOR_PAYMENT_METHOD",
                "missingFields": [],
                "customerMessage": "Para continuar, você prefere realizar o pagamento via PIX ou cartão de crédito?",
            },
        }).encode("utf-8")

        def opener(_req, timeout):
            raise error.HTTPError("http://internal", 409, "business rejection", {}, BytesIO(body))

        payload = json.loads(dispatch_tool(
            "prepare_payment", {"serviceType": "DECOR", "method": "link"},
            {"task_id": "session-1"}, PluginClient("http://internal", "token", opener)))

        self.assertFalse(payload["ok"])
        self.assertEqual(payload["error"], {
            "code": "PAYMENT_METHOD_INVALID",
            "nextAction": "ASK_FOR_PAYMENT_METHOD",
            "missingFields": [],
            "customerMessage": "Para continuar, você prefere realizar o pagamento via PIX ou cartão de crédito?",
        })

    def test_http_409_malformed_business_rejection_returns_generic_safe_failure(self):
        body = json.dumps({
            "ok": False,
            "error": {
                "code": "PAYMENT_METHOD_INVALID",
                "nextAction": "ASK_FOR_PAYMENT_METHOD",
                "missingFields": [],
            },
        }).encode("utf-8")

        def opener(_req, timeout):
            raise error.HTTPError("http://internal", 409, "malformed rejection", {}, BytesIO(body))

        payload = json.loads(dispatch_tool(
            "prepare_payment", {"serviceType": "DECOR", "method": "PIX"},
            {"task_id": "session-1"}, PluginClient("http://internal", "token", opener)))

        self.assertFalse(payload["ok"])
        self.assertEqual(payload["error"], SAFE_FAILURE)

    def test_http_409_technical_rejection_does_not_reach_hermes(self):
        body = json.dumps({
            "ok": False,
            "error": "database exception while committing payment",
            "trace": "java.lang.IllegalStateException: internal stack",
        }).encode("utf-8")

        def opener(_req, timeout):
            raise error.HTTPError("http://internal", 409, "technical conflict", {}, BytesIO(body))

        payload = json.loads(dispatch_tool(
            "prepare_payment", {"serviceType": "DECOR", "method": "PIX"},
            {"task_id": "session-1"}, PluginClient("http://internal", "token", opener)))
        rendered = json.dumps(payload, ensure_ascii=False).lower()

        self.assertFalse(payload["ok"])
        self.assertEqual(payload["error"], SAFE_FAILURE)
        for forbidden in ("database", "exception", "internal", "stack"):
            self.assertNotIn(forbidden, rendered)

    def test_http_500_technical_rejection_returns_generic_safe_failure(self):
        body = json.dumps({
            "ok": False,
            "error": {
                "code": "DATABASE_FAILURE",
                "nextAction": "RETRY",
                "missingFields": [],
                "customerMessage": "database connection exception",
            },
            "stack": "java.lang.RuntimeException: internal details",
        }).encode("utf-8")

        def opener(_req, timeout):
            raise error.HTTPError("http://internal", 500, "server failure", {}, BytesIO(body))

        payload = json.loads(dispatch_tool(
            "get_customer_profile", {}, {"task_id": "session-1"},
            PluginClient("http://internal", "token", opener)))
        rendered = json.dumps(payload, ensure_ascii=False).lower()

        self.assertFalse(payload["ok"])
        self.assertEqual(payload["error"], SAFE_FAILURE)
        for forbidden in ("database", "exception", "internal", "stack"):
            self.assertNotIn(forbidden, rendered)


if __name__ == "__main__":
    unittest.main()
