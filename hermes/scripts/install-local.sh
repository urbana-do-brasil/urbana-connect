#!/usr/bin/env bash
set -euo pipefail

HERMES_VERSION="v2026.8.3"
HERMES_COMMIT="3c27eb6234bf91b8ceee9e9071591b31e9b148cb"
HERMES_PACKAGE_VERSION="0.20.0"
HERMES_RELEASE_DATE="${HERMES_VERSION#v}"
HERMES_UV_IMAGE="ghcr.io/astral-sh/uv:0.11.6-python3.13-trixie"
HERMES_UV_CONFIG_DIGEST="sha256:4305d7cb6d01515f0738f328acd2f186b1bfc96f6b8ba0066b75b86fad16d699"
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

validate_profile_contract() {
  local profile_dir="$REPO_ROOT/hermes/profile"
  local plugin_dir="$REPO_ROOT/hermes/plugins/urbana-domain"

  [[ -f "$profile_dir/SOUL.md" ]] || { echo "Hermes profile SOUL.md is missing." >&2; exit 3; }
  [[ -f "$profile_dir/config.yaml.example" ]] || { echo "Hermes profile config is missing." >&2; exit 3; }
  [[ -f "$plugin_dir/plugin.yaml" ]] || { echo "urbana-domain plugin manifest is missing." >&2; exit 3; }
  [[ -f "$plugin_dir/__init__.py" ]] || { echo "urbana-domain plugin entrypoint is missing." >&2; exit 3; }

  grep -Eq '^[[:space:]]*-[[:space:]]+urbana-domain[[:space:]]*$' \
    "$profile_dir/config.yaml.example" || {
      echo "Hermes profile must enable only the urbana-domain plugin." >&2
      exit 3
    }
  grep -Fq 'api_server:' "$profile_dir/config.yaml.example" || {
    echo "Hermes profile must define the API-server toolset surface." >&2
    exit 3
  }
  grep -Eq '^[[:space:]]*-[[:space:]]+no_mcp[[:space:]]*$' \
    "$profile_dir/config.yaml.example" || {
      echo "Hermes profile must disable MCP toolsets." >&2
      exit 3
    }
  grep -Fq 'memory_enabled: false' "$profile_dir/config.yaml.example" || {
    echo "Hermes profile must disable global memory." >&2
    exit 3
  }
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
  grep -Fq './profile/SOUL.md:/opt/data/SOUL.md:ro' "$COMPOSE_FILE" || {
    echo "POC compose must mount the isolated SOUL.md read-only." >&2
    exit 3
  }
  grep -Fq './profile/config.yaml.example:/profile/config.yaml:ro' "$COMPOSE_FILE" || {
    echo "POC compose must stage the isolated config read-only." >&2
    exit 3
  }
  grep -Fq 'hermes-profile-init:' "$COMPOSE_FILE" || {
    echo "POC compose must stage the isolated config before Hermes starts." >&2
    exit 3
  }
  grep -Fq './plugins/urbana-domain:/opt/data/plugins/urbana-domain:ro' "$COMPOSE_FILE" || {
    echo "POC compose must mount only the urbana-domain plugin read-only." >&2
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
command -v git >/dev/null 2>&1 || { echo "Git is required to install pinned Hermes." >&2; exit 2; }
[[ -f "$ENV_FILE" ]] || { echo "Create .env.poc from .env.poc.example first." >&2; exit 2; }
env_mode="$(file_mode "$ENV_FILE")"
if [[ "$env_mode" != "600" ]]; then
  echo ".env.poc must have mode 600 (reported: $env_mode). Run: chmod 600 .env.poc" >&2
  exit 2
fi
docker info >/dev/null 2>&1 || { echo "Docker Desktop is not available." >&2; exit 2; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 is required." >&2; exit 2; }
[[ -f "$COMPOSE_FILE" ]] || { echo "POC compose file is missing." >&2; exit 3; }
validate_profile_contract
validate_compose_isolation

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

revision="$(image_label 'org.opencontainers.image.revision')"
version_label="$(image_label 'org.opencontainers.image.version')"
if [[ "$revision" != "$HERMES_COMMIT" || "$version_label" != "$HERMES_VERSION" ]]; then
  echo "Building isolated Hermes $HERMES_VERSION ($HERMES_COMMIT)..."
  build_context="$(mktemp -d "${TMPDIR:-/tmp}/urbana-hermes-build.XXXXXX")"
  cleanup_build_context() {
    if [[ -n "${build_context:-}" && -d "$build_context" ]]; then
      rm -rf -- "$build_context"
    fi
  }
  trap cleanup_build_context EXIT
  GIT_TERMINAL_PROMPT=0 git -c advice.detachedHead=false clone --quiet --depth 1 \
    --branch "$HERMES_VERSION" https://github.com/NousResearch/hermes-agent.git "$build_context"
  checked_out_revision="$(git -C "$build_context" rev-parse HEAD)"
  if [[ "$checked_out_revision" != "$HERMES_COMMIT" ]]; then
    echo "Hermes tag $HERMES_VERSION resolved to unexpected revision $checked_out_revision." >&2
    exit 3
  fi

  build_dockerfile="$build_context/Dockerfile"
  local_uv_id="$(docker image inspect "$HERMES_UV_IMAGE" --format '{{.Id}}' 2>/dev/null || true)"
  local_uv_version="$(docker image inspect "$HERMES_UV_IMAGE" \
    --format '{{ index .Config.Labels "org.opencontainers.image.version" }}' 2>/dev/null || true)"
  if [[ "$local_uv_id" == "$HERMES_UV_CONFIG_DIGEST" && \
        "$local_uv_version" == "0.11.6-python3.13-trixie" ]]; then
    # Docker Desktop can have the exact content-addressed base locally while
    # its daemon cannot obtain an anonymous GHCR token. Use only that exact
    # config digest; never silently substitute another uv image.
    build_dockerfile="$build_context/Dockerfile.local"
    awk -v replacement="FROM ${HERMES_UV_IMAGE} AS uv_source" \
      'BEGIN { source = "FROM ghcr.io/astral-sh/uv:0.11.6-python3.13-trixie@sha256:b3c543b6c4f23a5f2df22866bd7857e5d304b67a564f4feab6ac22044dde719b AS uv_source" }
       $0 == source { print replacement; next }
       { print }' \
      "$build_context/Dockerfile" > "$build_dockerfile"
    grep -Fq "FROM ${HERMES_UV_IMAGE} AS uv_source" "$build_dockerfile" || {
      echo "Failed to prepare the verified local uv base." >&2
      exit 3
    }
    echo "Using locally verified uv base content $HERMES_UV_CONFIG_DIGEST."
  fi
  docker build \
    --build-arg "HERMES_GIT_SHA=$HERMES_COMMIT" \
    --label "org.opencontainers.image.version=$HERMES_VERSION" \
    --label "org.opencontainers.image.revision=$HERMES_COMMIT" \
    --label "org.opencontainers.image.source=https://github.com/NousResearch/hermes-agent" \
    --tag "$IMAGE" \
    --file "$build_dockerfile" \
    "$build_context"
fi

revision="$(image_label 'org.opencontainers.image.revision')"
version_label="$(image_label 'org.opencontainers.image.version')"
if [[ "$revision" != "$HERMES_COMMIT" || "$version_label" != "$HERMES_VERSION" ]]; then
  echo "Installed Hermes image labels do not match $HERMES_VERSION ($HERMES_COMMIT)." >&2
  exit 3
fi

if ! reported="$(docker run --rm --network none --read-only --cap-drop ALL \
  --entrypoint /opt/hermes/.venv/bin/hermes "$IMAGE" --version 2>/dev/null)"; then
  echo "Hermes image version probe failed." >&2
  exit 3
fi
if [[ "$reported" != *"Hermes Agent v${HERMES_PACKAGE_VERSION}"* || \
      "$reported" != *"(${HERMES_RELEASE_DATE})"* ]]; then
  echo "Hermes image did not report $HERMES_PACKAGE_VERSION (${HERMES_RELEASE_DATE})." >&2
  exit 3
fi

if ! "${compose[@]}" config --quiet; then
  echo "Docker Compose POC validation failed." >&2
  exit 3
fi
echo "Hermes container image ready: $IMAGE"
