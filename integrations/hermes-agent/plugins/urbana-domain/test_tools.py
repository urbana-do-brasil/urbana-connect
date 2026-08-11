import json
import unittest

import __init__ as plugin
from tools import PluginClient, TOOL_NAMES, dispatch_tool


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

    def test_surface_contains_only_six_domain_tools(self):
        self.assertEqual(len(TOOL_NAMES), 6)
        self.assertEqual(set(TOOL_NAMES), {
            "get_customer_profile", "update_customer_fact", "list_available_services",
            "prepare_terms", "prepare_payment", "request_human_handoff",
        })

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
        self.assertIsInstance(payload["error"], str)

    def test_handler_catches_missing_runtime_session(self):
        payload = json.loads(dispatch_tool("list_available_services", {}, {}, None))
        self.assertFalse(payload["ok"])
        self.assertIn("session", payload["error"])


if __name__ == "__main__":
    unittest.main()
