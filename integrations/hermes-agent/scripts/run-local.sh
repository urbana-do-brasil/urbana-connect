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
if [[ "$IMAGE" == "$DEFAULT_IMAGE" ]]; then
  echo "Building validated Hermes image: $IMAGE"
  "${compose[@]}" build hermes
else
  for argument in "$@"; do
    if [[ "$argument" == "--build" ]]; then
      echo "Do not use --build with an explicitly overridden HERMES_IMAGE ($IMAGE)." >&2
      echo "Validate that image separately, then run without --build." >&2
      exit 3
    fi
  done
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

exec "${compose[@]}" up "$@"
