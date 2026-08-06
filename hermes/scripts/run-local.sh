#!/usr/bin/env bash
set -euo pipefail

HERMES_VERSION="v2026.8.3"
HERMES_COMMIT="3c27eb6234bf91b8ceee9e9071591b31e9b148cb"
HERMES_PACKAGE_VERSION="0.20.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/hermes/docker-compose.poc.yml"
ENV_FILE="$REPO_ROOT/.env.poc"
IMAGE="urbana-hermes-agent:${HERMES_PACKAGE_VERSION}"

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

command -v docker >/dev/null 2>&1 || { echo "Docker is required." >&2; exit 2; }
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

revision="$(image_label 'org.opencontainers.image.revision')"
version_label="$(image_label 'org.opencontainers.image.version')"
if [[ "$revision" != "$HERMES_COMMIT" || "$version_label" != "$HERMES_VERSION" ]]; then
  echo "Pinned Hermes image is not installed; run hermes/scripts/install-local.sh first." >&2
  exit 3
fi

exec "${compose[@]}" up "$@"
