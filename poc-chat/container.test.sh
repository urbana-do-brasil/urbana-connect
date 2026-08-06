#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$REPO_ROOT/hermes/docker-compose.poc.yml}"
ENV_FILE="${ENV_FILE:-$REPO_ROOT/.env.poc}"
CHAT_URL="${CHAT_URL:-http://127.0.0.1:${POC_CHAT_HOST_PORT:-3000}}"

fail() {
  echo "container contract failed: $*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "missing required file: $1"
}

require_file "$REPO_ROOT/poc-chat/Dockerfile"
require_file "$REPO_ROOT/poc-chat/nginx/nginx.conf"
require_file "$REPO_ROOT/poc-chat/nginx/default.conf.template"
require_file "$REPO_ROOT/poc-chat/docker-entrypoint.d/10-poc-token.sh"

grep -Eq '^FROM node:24-alpine' "$REPO_ROOT/poc-chat/Dockerfile" \
  || fail "Dockerfile has no Node 24 build stage"
grep -Eq '^FROM nginxinc/nginx-unprivileged:' "$REPO_ROOT/poc-chat/Dockerfile" \
  || fail "Dockerfile has no unprivileged Nginx runtime"
grep -Eq '^USER 101(:101)?$' "$REPO_ROOT/poc-chat/Dockerfile" \
  || fail "runtime image does not select the unprivileged UID"
grep -Fq 'proxy_set_header Authorization "Bearer ${HERMES_POC_API_TOKEN}";' \
  "$REPO_ROOT/poc-chat/nginx/default.conf.template" \
  || fail "proxy does not inject the server-side token"
grep -Eq 'proxy_set_header[[:space:]]+Authorization[[:space:]]+\$http_authorization' \
  "$REPO_ROOT/poc-chat/nginx/default.conf.template" \
  && fail "proxy forwards browser Authorization"
grep -Eq 'add_header[[:space:]]+Access-Control-Allow-Origin' "$REPO_ROOT/poc-chat/nginx/default.conf.template" \
  && fail "proxy enables CORS"
rg -Uq 'location\s+/api/\s*\{\s*return\s+404;' \
  "$REPO_ROOT/poc-chat/nginx/default.conf.template" \
  || fail "API catch-all is not denied"

if [[ "${RUN_DOCKER_CHECKS:-0}" != "1" ]]; then
  echo "container contract static checks passed; set RUN_DOCKER_CHECKS=1 for live checks."
  exit 0
fi

command -v docker >/dev/null 2>&1 || fail "docker is required for live checks"
[[ -f "$ENV_FILE" ]] || fail "missing env file for Compose checks: $ENV_FILE"

compose_args=(--env-file "$ENV_FILE" -f "$COMPOSE_FILE")
docker compose "${compose_args[@]}" config --quiet \
  || fail "docker compose config rejected the local stack"

docker compose "${compose_args[@]}" exec -T poc-chat nginx -t \
  || fail "nginx -t rejected the loaded runtime configuration"

health_status="$(curl --connect-timeout 3 --max-time 5 --silent --output /dev/null \
  --write-out '%{http_code}' "$CHAT_URL/health")"
[[ "$health_status" == "200" ]] || fail "/health returned HTTP $health_status"

health_body="$(curl --connect-timeout 3 --max-time 5 --silent "$CHAT_URL/health")"
[[ "$health_body" == "ok" ]] || fail "/health did not return ok"

blocked_status="$(curl --connect-timeout 3 --max-time 5 --silent --output /dev/null \
  --write-out '%{http_code}' "$CHAT_URL/api/poc/conversations/metrics")"
[[ "$blocked_status" == "404" || "$blocked_status" == "405" ]] \
  || fail "metrics route returned HTTP $blocked_status"

flush_status="$(curl --connect-timeout 3 --max-time 5 --silent --output /dev/null \
  --write-out '%{http_code}' -X POST "$CHAT_URL/api/poc/conversations/manual-00000000-0000-0000-0000-000000000000/flush")"
[[ "$flush_status" == "404" || "$flush_status" == "405" ]] \
  || fail "flush route returned HTTP $flush_status"

cors_headers="$(curl --connect-timeout 3 --max-time 5 --silent --dump-header - \
  -H 'Origin: https://unexpected.invalid' "$CHAT_URL/health")"
if grep -qi '^access-control-allow-origin:' <<<"$cors_headers"; then
  fail "health response contains a CORS allow header"
fi

echo "container contract live checks passed."
