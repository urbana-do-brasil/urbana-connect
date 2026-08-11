#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(CDPATH='' cd -- "$SCRIPT_DIR/../.." && pwd)"

failures=0

require_path() {
  local relative_path="$1"
  if [[ ! -e "$REPO_ROOT/$relative_path" ]]; then
    printf 'MISSING %s\n' "$relative_path" >&2
    failures=$((failures + 1))
  fi
}

for required_path in \
  apps/urbana-connect-api/build.gradle \
  apps/urbana-connect-api/src \
  apps/poc-chat/package.json \
  apps/poc-chat/src \
  integrations/hermes-agent/profile \
  integrations/hermes-agent/scripts \
  infra/local-poc \
  infra/kubernetes \
  quality/conversation-corpus \
  quality/system-e2e \
  contracts \
  README.md; do
  require_path "$required_path"
done

while IFS= read -r integration_entry; do
  case "$(basename -- "$integration_entry")" in
    README.md|profile|plugins|scripts) ;;
    *)
      printf 'OWNERSHIP unexpected Hermes integration entry: %s\n' \
        "${integration_entry#"$REPO_ROOT/"}" >&2
      failures=$((failures + 1))
      ;;
  esac
done < <(find "$REPO_ROOT/integrations/hermes-agent" -mindepth 1 -maxdepth 1 -print 2>/dev/null)

if [[ -d "$REPO_ROOT/app" || -d "$REPO_ROOT/poc-chat" || -d "$REPO_ROOT/hermes" || -d "$REPO_ROOT/corpus" ]]; then
  printf 'LEGACY_ROOT still contains an application/runtime source directory\n' >&2
  failures=$((failures + 1))
fi

if ! git -C "$REPO_ROOT" check-ignore -q .env.poc; then
  printf 'SECRET_POLICY .env.poc is not ignored\n' >&2
  failures=$((failures + 1))
fi

if git -C "$REPO_ROOT" ls-files --error-unmatch .env.poc >/dev/null 2>&1; then
  printf 'SECRET_POLICY .env.poc is tracked\n' >&2
  failures=$((failures + 1))
fi

if [[ -d "$REPO_ROOT/apps/poc-chat/src" ]] && rg -n -i \
  --glob '!**/*.test.*' --glob '!**/test/**' \
  'hermes|mongodb|mongo' "$REPO_ROOT/apps/poc-chat/src" >/dev/null 2>&1; then
  printf 'BOUNDARY browser production source references Hermes/Mongo directly\n' >&2
  failures=$((failures + 1))
fi

if [[ "$failures" -ne 0 ]]; then
  printf 'repository structure contract: FAIL (%d finding(s))\n' "$failures" >&2
  exit 1
fi

printf 'repository structure contract: PASS\n'
