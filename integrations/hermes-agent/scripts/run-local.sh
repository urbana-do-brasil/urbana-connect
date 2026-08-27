#!/usr/bin/env bash
set -euo pipefail

HERMES_VERSION="v2026.8.3-pee-103"
HERMES_COMMIT="2f5472a15a026b6bd5847ad65058f1565d2b40ba"
DEFAULT_IMAGE="urbana-hermes-agent:pee-103-2f5472a15"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/infra/local-poc/docker-compose.poc.yml"
ENV_FILE="$REPO_ROOT/.env.poc"
IMAGE=""

app_source_revision() {
  local revision
  revision="$(git -C "$REPO_ROOT" rev-parse --short=12 HEAD 2>/dev/null || printf 'workspace-source')"
  if ! git -C "$REPO_ROOT" diff --quiet -- apps/urbana-connect-api; then
    revision="${revision}-dirty"
  fi
  printf '%s' "$revision"
}

wait_for_healthy_service() {
  local service="$1"
  local attempts=60
  local container_id health_status

  while (( attempts > 0 )); do
    container_id="$("${compose[@]}" ps -q "$service")"
    if [[ -n "$container_id" ]]; then
      health_status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
      if [[ "$health_status" == "healthy" ]]; then
        echo "$service is healthy."
        return 0
      fi
      if [[ "$health_status" == "unhealthy" || "$health_status" == "exited" || "$health_status" == "dead" ]]; then
        echo "$service did not become healthy (status: $health_status)." >&2
        return 1
      fi
    fi
    sleep 2
    ((attempts--))
  done

  echo "Timed out waiting for $service to become healthy." >&2
  return 1
}

file_mode() {
  if [[ "$(uname -s)" == "Darwin" ]]; then
    stat -f '%Lp' "$1"
  else
    stat -c '%a' "$1"
  fi
}

validate_compose_isolation() {
  grep -Fq 'HERMES_HOME: /opt/data' "$COMPOSE_FILE" || {
    echo "POC compose must use its own Hermes home." >&2
    exit 3
  }
  grep -Fq 'hermes_data:/opt/data' "$COMPOSE_FILE" || {
    echo "POC compose must persist Hermes state in its isolated volume." >&2
    exit 3
  }
  if grep -Eq '(\$HOME|~/.hermes|/\.hermes)' "$COMPOSE_FILE"; then
    echo "POC compose must not reference the personal Hermes profile." >&2
    exit 3
  fi
}

image_label() {
  docker image inspect --format "{{ index .Config.Labels \"$1\" }}" \
    "$IMAGE" 2>/dev/null || true
}

compose_image() {
  "${compose[@]}" config --format json | python3 -c '
import json
import sys

services = json.load(sys.stdin).get("services", {})
image = services.get("hermes", {}).get("image")
if not image:
    raise SystemExit("Hermes service image is missing from the resolved Compose config.")
print(image)
'
}

command -v docker >/dev/null 2>&1 || { echo "Docker is required." >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "Python 3 is required." >&2; exit 2; }
[[ -f "$ENV_FILE" ]] || { echo "Create .env.poc from .env.poc.example first." >&2; exit 2; }
env_mode="$(file_mode "$ENV_FILE")"
[[ "$env_mode" == "600" ]] || {
  echo ".env.poc must have mode 600. Run: chmod 600 .env.poc" >&2
  exit 2
}
docker info >/dev/null 2>&1 || { echo "Docker Desktop is not available." >&2; exit 2; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 is required." >&2; exit 2; }
[[ -f "$COMPOSE_FILE" ]] || { echo "POC compose file is missing." >&2; exit 3; }
validate_compose_isolation

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
if ! "${compose[@]}" config --quiet; then
  echo "Docker Compose POC validation failed." >&2
  exit 3
fi

IMAGE="$(compose_image)"
app_revision="$(app_source_revision)"
app_build_created="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
if [[ "$IMAGE" == "$DEFAULT_IMAGE" ]]; then
  echo "Building validated Hermes image: $IMAGE"
  "${compose[@]}" build hermes
else
  docker image inspect "$IMAGE" >/dev/null 2>&1 || {
    echo "Configured HERMES_IMAGE is not available locally: $IMAGE" >&2
    exit 3
  }
fi

revision="$(image_label 'org.opencontainers.image.revision')"
version_label="$(image_label 'org.opencontainers.image.version')"
if [[ "$IMAGE" == "$DEFAULT_IMAGE" &&
      ( "$revision" != "$HERMES_COMMIT" || "$version_label" != "$HERMES_VERSION" ) ]]; then
  echo "Validated Hermes image labels do not match $HERMES_VERSION ($HERMES_COMMIT)." >&2
  exit 3
fi

echo "Building current Urbana Connect and POC chat images (source revision: $app_revision)."
"${compose[@]}" build \
  --build-arg "APP_SOURCE_REVISION=$app_revision" \
  --build-arg "APP_BUILD_CREATED=$app_build_created" \
  urbana-connect poc-chat

up_args=(--force-recreate)
detached=false
for argument in "$@"; do
  case "$argument" in
    --build)
      echo "Ignoring --build: selected images were built explicitly above."
      ;;
    -d|--detach)
      detached=true
      up_args+=("$argument")
      ;;
    --no-build)
      ;;
    *)
      up_args+=("$argument")
      ;;
  esac
done

if [[ "$detached" != true ]]; then
  exec "${compose[@]}" up --no-build "${up_args[@]}"
fi

"${compose[@]}" up --no-build "${up_args[@]}"
wait_for_healthy_service urbana-connect
wait_for_healthy_service poc-chat

backend_image_id="$("${compose[@]}" images -q urbana-connect)"
if [[ -n "$backend_image_id" ]]; then
  docker image inspect --format \
    'urbana-connect image={{.Id}} revision={{index .Config.Labels "org.opencontainers.image.revision"}} build={{index .Config.Labels "br.com.urbana.connect.build"}}' \
    "$backend_image_id"
fi
