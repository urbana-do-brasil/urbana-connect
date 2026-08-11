#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/infra/local-poc/docker-compose.poc.yml"
ENV_FILE="$REPO_ROOT/.env.poc"
compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

hermes_id="$("${compose[@]}" ps --quiet hermes)"
[[ -n "$hermes_id" ]] || { echo "Hermes container is not running." >&2; exit 2; }

docker inspect "$hermes_id" | python3 -c '
import json, sys
container = json.load(sys.stdin)[0]
mounts = container.get("Mounts", [])
binds = [m for m in mounts if m.get("Type") == "bind"]
allowed = {"SOUL.md", "urbana-domain"}
actual = {m.get("Source", "").rstrip("/").split("/")[-1] for m in binds}
if actual != allowed or any(m.get("RW") for m in binds):
    raise SystemExit(f"unexpected Hermes bind mounts: {binds}")
if any("docker.sock" in m.get("Source", "") for m in mounts):
    raise SystemExit("Docker socket must not be mounted")
print("filesystem_isolation=ok")
'

init_id="$("${compose[@]}" ps --quiet --all hermes-profile-init)"
[[ -n "$init_id" ]] || { echo "Hermes profile init container is missing." >&2; exit 2; }
docker inspect "$init_id" | python3 -c '
import json, sys
container = json.load(sys.stdin)[0]
binds = [m for m in container.get("Mounts", []) if m.get("Type") == "bind"]
actual = {m.get("Source", "").rstrip("/").split("/")[-1] for m in binds}
if actual != {"config.yaml.example"} or any(m.get("RW") for m in binds):
    raise SystemExit(f"unexpected profile init bind mounts: {binds}")
print("profile_config_staging=read-only")
'

"${compose[@]}" exec -T hermes python - <<'PY'
import socket
import urllib.request

def must_be_blocked(host, port):
    try:
        with socket.create_connection((host, port), timeout=3):
            pass
    except OSError:
        return
    raise SystemExit(f"unexpected direct connectivity to {host}:{port}")

must_be_blocked("mongodb", 27017)
must_be_blocked("host.docker.internal", 8642)
must_be_blocked("example.com", 443)

with urllib.request.urlopen("http://urbana-connect:8081/api/v1/health", timeout=5) as response:
    if response.status != 200:
        raise SystemExit(f"Urbana Connect health rejected: {response.status}")
print("network_isolation=ok")
PY

echo "Hermes isolation smoke passed."
