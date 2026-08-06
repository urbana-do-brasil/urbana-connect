#!/usr/bin/env bash
set -euo pipefail

# Contract smoke is intentionally LLM-free. It validates the native API
# server/session/tool-surface contract; set HERMES_LIVE_MODEL_SMOKE=1 to add a
# real chat turn (which requires a configured OpenRouter credential).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$REPO_ROOT/.env.poc"
EXPECTED_PACKAGE_VERSION="0.20.0"

command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required." >&2; exit 2; }

file_mode() {
  if [[ "$(uname -s)" == "Darwin" ]]; then
    stat -f '%Lp' "$1"
  else
    stat -c '%a' "$1"
  fi
}

# Read only the requested assignment from .env.poc. Do not source the file:
# values are secrets, while shell evaluation would turn a malformed local
# dotenv file into executable code.
dotenv_value() {
  local key="$1"
  python3 - "$ENV_FILE" "$key" <<'PY'
import sys

path, wanted = sys.argv[1:]
with open(path, encoding="utf-8") as handle:
    for raw_line in handle:
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        name, separator, value = line.partition("=")
        if separator and name.strip() == wanted:
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            print(value)
            break
PY
}

if [[ -f "$ENV_FILE" ]]; then
  env_mode="$(file_mode "$ENV_FILE")"
  if [[ "$env_mode" != "600" ]]; then
    echo ".env.poc must have mode 600 (reported: $env_mode). Run: chmod 600 .env.poc" >&2
    exit 2
  fi
fi

BASE_URL="${HERMES_BASE_URL:-}"
if [[ -z "$BASE_URL" && -f "$ENV_FILE" ]]; then
  BASE_URL="$(dotenv_value HERMES_BASE_URL)"
fi
BASE_URL="${BASE_URL:-http://127.0.0.1:8652}"

API_KEY="${HERMES_API_SERVER_KEY:-}"
if [[ -z "$API_KEY" && -f "$ENV_FILE" ]]; then
  API_KEY="$(dotenv_value HERMES_API_SERVER_KEY)"
fi
if [[ -z "$API_KEY" ]]; then
  echo "HERMES_API_SERVER_KEY is not set; smoke-contract requires a local server." >&2
  exit 2
fi

MODEL="${HERMES_MODEL:-}"
if [[ -z "$MODEL" && -f "$ENV_FILE" ]]; then
  MODEL="$(dotenv_value HERMES_MODEL)"
fi
MODEL="${MODEL:-openai/gpt-5.6-luna}"

REASONING_EFFORT="${HERMES_REASONING_EFFORT:-}"
if [[ -z "$REASONING_EFFORT" && -f "$ENV_FILE" ]]; then
  REASONING_EFFORT="$(dotenv_value HERMES_REASONING_EFFORT)"
fi
REASONING_EFFORT="${REASONING_EFFORT:-max}"

OPENROUTER_KEY="${OPENROUTER_API_KEY:-}"
if [[ -z "$OPENROUTER_KEY" && -f "$ENV_FILE" ]]; then
  OPENROUTER_KEY="$(dotenv_value OPENROUTER_API_KEY)"
fi
OR_KEY="${OR_API_KEY:-}"
if [[ -z "$OR_KEY" && -f "$ENV_FILE" ]]; then
  OR_KEY="$(dotenv_value OR_API_KEY)"
fi

ROOT="${BASE_URL%/}"
curl_json() {
  printf 'Authorization: Bearer %s\nContent-Type: application/json\n' "$API_KEY" \
    | curl --connect-timeout 5 --max-time 60 --fail --silent --show-error --header @- "$@"
}
curl_status() {
  printf 'Authorization: Bearer %s\nContent-Type: application/json\n' "$API_KEY" \
    | curl --connect-timeout 5 --max-time 15 --silent --show-error \
      --output /dev/null --write-out '%{http_code}' --header @- "$@"
}

health="$(curl_json "$ROOT/health")"
capabilities="$(curl_json "$ROOT/v1/capabilities")"
toolsets="$(curl_json "$ROOT/v1/toolsets")"

python3 - "$health" "$capabilities" "$toolsets" "$EXPECTED_PACKAGE_VERSION" <<'PY'
import json
import sys

health, capabilities, toolsets = map(json.loads, sys.argv[1:4])
expected_version = sys.argv[4]
if health.get("status") != "ok" or health.get("platform") != "hermes-agent" \
        or health.get("version") != expected_version:
    raise SystemExit(f"health rejected: {health}")

features = capabilities.get("features", {})
for feature in ("session_resources", "session_chat"):
    if features.get(feature) is not True:
        raise SystemExit(f"capabilities missing {feature}: {capabilities}")

endpoints = capabilities.get("endpoints", {})
toolset_endpoint = endpoints.get("toolsets")
if not isinstance(toolset_endpoint, dict) or toolset_endpoint.get("method") != "GET" \
        or toolset_endpoint.get("path") != "/v1/toolsets":
    raise SystemExit(f"capabilities toolsets endpoint rejected: {capabilities}")
required_endpoints = {
    "sessions": ("GET", "/api/sessions"),
    "session_create": ("POST", "/api/sessions"),
    "session_messages": ("GET", "/api/sessions/{session_id}/messages"),
    "session_delete": ("DELETE", "/api/sessions/{session_id}"),
}
for name, (method, path) in required_endpoints.items():
    endpoint = endpoints.get(name)
    if not isinstance(endpoint, dict) or endpoint.get("method") != method \
            or endpoint.get("path") != path:
        raise SystemExit(f"capabilities endpoint {name} rejected: {capabilities}")

data = toolsets.get("data")
if not isinstance(data, list):
    raise SystemExit(f"toolsets response rejected: {toolsets}")
enabled = [item for item in data if isinstance(item, dict) and item.get("enabled") is True]
if [item.get("name") for item in enabled] != ["urbana-domain"]:
    raise SystemExit(f"enabled toolsets rejected: {enabled}")
expected = {
    "get_customer_profile", "update_customer_fact", "list_available_services",
    "prepare_terms", "prepare_payment", "request_human_handoff",
}
actual_tools = enabled[0].get("tools", []) if enabled else []
actual = set(actual_tools)
if len(actual_tools) != len(expected) or actual != expected:
    raise SystemExit(f"domain tools rejected: {sorted(actual)}")
PY

session_request="$(python3 - <<'PY'
import json
import os

print(json.dumps({"title": "contract-smoke-" + str(os.getpid())}))
PY
)"
created="$(curl_json -X POST "$ROOT/api/sessions" --data "$session_request")"
session_id="$(python3 - "$created" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
session = payload.get("session") or {}
value = session.get("id")
if not isinstance(value, str) or not value:
    raise SystemExit(f"create session response rejected: {payload}")
print(value)
PY
)"
cleanup() {
  if [[ -n "${session_id:-}" ]]; then
    curl_json -X DELETE "$ROOT/api/sessions/${session_id}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

messages="$(curl_json "$ROOT/api/sessions/${session_id}/messages")"
python3 - "$messages" "$session_id" <<'PY'
import json
import sys

payload, expected = json.loads(sys.argv[1]), sys.argv[2]
if payload.get("object") != "list" or payload.get("session_id") != expected \
        or not isinstance(payload.get("data"), list):
    raise SystemExit(f"messages response rejected: {payload}")
PY

live_requested=0
case "${HERMES_LIVE_MODEL_SMOKE:-0}" in
  1|true|True|TRUE|yes|Yes|YES|on|On|ON) live_requested=1 ;;
esac

if [[ "$live_requested" == "1" ]]; then
  if [[ -z "$OPENROUTER_KEY" && -z "$OR_KEY" ]]; then
    echo "HERMES_LIVE_MODEL_SMOKE requires OPENROUTER_API_KEY or OR_API_KEY; contract smoke passed." >&2
    exit 6
  fi
  chat_request="$(python3 - "$MODEL" "$REASONING_EFFORT" <<'PY'
import json
import sys

model, reasoning_effort = sys.argv[1:]
print(json.dumps({
    "message": "Responda apenas OK.",
    "model": model,
    "provider": "openrouter",
    "model_options": {"reasoning_effort": reasoning_effort},
}, separators=(",", ":")))
PY
)"
  chat="$(curl_json -X POST "$ROOT/api/sessions/${session_id}/chat" --data "$chat_request")"
  python3 - "$chat" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
message = payload.get("message") or {}
if not isinstance(message.get("content"), str) or not message["content"].strip():
    raise SystemExit(f"live chat response rejected: {payload}")
if not isinstance(payload.get("session_id"), str) or not payload["session_id"]:
    raise SystemExit(f"live chat session response rejected: {payload}")
PY
fi

deleted="$(curl_json -X DELETE "$ROOT/api/sessions/${session_id}")"
python3 - "$deleted" "$session_id" <<'PY'
import json
import sys

payload, expected_id = json.loads(sys.argv[1]), sys.argv[2]
if payload.get("id") != expected_id or payload.get("deleted") is not True:
    raise SystemExit(f"delete session response rejected: {payload}")
PY
post_delete_status="$(curl_status "$ROOT/api/sessions/${session_id}")"
if [[ "$post_delete_status" != "404" ]]; then
  echo "Deleted session remained addressable (HTTP $post_delete_status)." >&2
  exit 3
fi
session_id=""

if [[ "$live_requested" == "1" ]]; then
  echo "Hermes contract smoke passed ($ROOT); live model smoke passed."
else
  echo "Hermes contract smoke passed ($ROOT); live model smoke not requested."
fi
