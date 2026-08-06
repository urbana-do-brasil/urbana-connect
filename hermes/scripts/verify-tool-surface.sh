#!/usr/bin/env bash
set -euo pipefail

command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 2; }

BASE_URL="${HERMES_BASE_URL:-http://127.0.0.1:8652}"
API_KEY="${HERMES_API_SERVER_KEY:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$REPO_ROOT/.env.poc"

dotenv_value() {
  local key="$1"
  python3 - "$ENV_FILE" "$key" <<'PY'
import sys

path, wanted = sys.argv[1:]
for raw_line in open(path, encoding="utf-8"):
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

if [[ -z "$API_KEY" && -f "$ENV_FILE" ]]; then
  API_KEY="$(dotenv_value HERMES_API_SERVER_KEY)"
fi
[[ -n "$API_KEY" ]] || { echo "HERMES_API_SERVER_KEY is required" >&2; exit 2; }

ROOT="${BASE_URL%/}"
toolsets="$(curl --connect-timeout 5 --max-time 30 --fail --silent --show-error \
  -H "Authorization: Bearer $API_KEY" -H "Content-Type: application/json" \
  "$ROOT/v1/toolsets")"

python3 - "$toolsets" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
enabled = [item for item in payload.get("data", [])
           if isinstance(item, dict) and item.get("enabled") is True]
if [item.get("name") for item in enabled] != ["urbana-domain"]:
    raise SystemExit(f"unexpected enabled toolsets: {enabled}")
expected = {
    "get_customer_profile", "update_customer_fact", "list_available_services",
    "prepare_terms", "prepare_payment", "request_human_handoff",
}
actual = set(enabled[0].get("tools", []))
if actual != expected:
    raise SystemExit(f"unexpected urbana-domain tools: {sorted(actual)}")
print("tool_surface=urbana-domain:6");
PY
