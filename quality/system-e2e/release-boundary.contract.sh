#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
failures=0

fail() {
  printf 'release boundary: FAIL: %s\n' "$1" >&2
  failures=$((failures + 1))
}

pass() {
  printf 'release boundary: PASS: %s\n' "$1"
}

for directory in \
  apps/urbana-connect-api \
  apps/poc-chat \
  integrations/hermes-agent \
  infra/local-poc \
  infra/kubernetes \
  quality/conversation-corpus \
  quality/system-e2e \
  contracts; do
  if [[ -d "$REPO_ROOT/$directory" ]]; then
    pass "canonical directory $directory exists"
  else
    fail "canonical directory $directory is missing"
  fi
done

for legacy in app poc-chat hermes corpus infra/k8s; do
  if [[ -e "$REPO_ROOT/$legacy" ]]; then
    fail "legacy source path remains: $legacy"
  else
    pass "legacy source path absent: $legacy"
  fi
done

if [[ -f "$REPO_ROOT/.env.poc" ]]; then
  if git -C "$REPO_ROOT" check-ignore -q .env.poc; then
    pass ".env.poc is ignored"
  else
    fail ".env.poc exists but is not ignored"
  fi
else
  pass ".env.poc is not present in this checkout"
fi

if git -C "$REPO_ROOT" ls-files | rg -q '(^|/)\.env\.poc$|(^|/)\.codex(/|$)'; then
  fail "real POC env or Codex-local path is tracked"
else
  pass "real POC env and Codex-local paths are not tracked"
fi

generated_pattern='(^|/)(node_modules|dist|coverage|build|test-results|playwright-report|results|\.gradle)(/|$)'
if git -C "$REPO_ROOT" ls-files | rg -q "$generated_pattern"; then
  fail "generated directory is tracked"
else
  pass "generated directories are not tracked"
fi

if git -C "$REPO_ROOT" ls-files | rg -q '(^|/)\.DS_Store$|(^|/)\.idea(/|$)'; then
  fail "local IDE/OS artifact is tracked"
else
  pass "local IDE/OS artifacts are not tracked"
fi

if find "$REPO_ROOT/apps" "$REPO_ROOT/integrations" "$REPO_ROOT/quality" "$REPO_ROOT/specs" \
  -type f -name '* 2.*' \
  -not -path '*/node_modules/*' \
  -not -path '*/build/*' \
  -not -path '*/.gradle/*' \
  -not -path '*/coverage/*' \
  -print -quit | rg -q .; then
  fail "duplicate source/document file with suffix ' 2' remains"
else
  pass "duplicate source/document files are absent"
fi

if ! rg -q '^\.codex/$' "$REPO_ROOT/.gitignore"; then
  fail ".gitignore does not exclude .codex/"
else
  pass ".gitignore excludes .codex/"
fi

workflow_invalid=0
while IFS= read -r line; do
  action="$(printf '%s\n' "$line" | sed -E 's/.*uses:[[:space:]]+([^[:space:]#]+).*/\1/')"
  sha="${action##*@}"
  if [[ ! "$sha" =~ ^[0-9a-f]{40}$ ]]; then
    workflow_invalid=1
  fi
done < <(rg '^[[:space:]]+uses:' "$REPO_ROOT/.github/workflows" || true)
if ((workflow_invalid)); then
  fail "workflow contains an invalid Action SHA"
else
  pass "workflow Action SHAs have 40 hexadecimal characters"
fi

if awk '
  /^  urbana-connect:/ { in_service = 1 }
  in_service { print }
  in_service && /^  [[:alnum:]_-]+:/ && $0 !~ /^  urbana-connect:/ { exit }
' "$REPO_ROOT/infra/local-poc/docker-compose.poc.yml" | rg -q 'healthcheck:'; then
  pass "Urbana Connect has a Compose healthcheck"
else
  fail "Urbana Connect has no Compose healthcheck"
fi

if rg -n -A20 '^  poc-chat:' "$REPO_ROOT/infra/local-poc/docker-compose.poc.yml" \
  | rg -q 'condition: service_healthy'; then
  pass "poc-chat waits for a healthy API"
else
  fail "poc-chat does not wait for a healthy API"
fi

if [[ "${RELEASE_BOUNDARY_CHECK_STAGED:-0}" == "1" ]]; then
  for directory in \
    apps/urbana-connect-api \
    apps/poc-chat \
    integrations/hermes-agent \
    infra/local-poc \
    infra/kubernetes \
    quality/conversation-corpus \
    quality/system-e2e \
    contracts \
    specs/006-stage1-release-hardening; do
    if git -C "$REPO_ROOT" ls-files -- "$directory" | rg -q .; then
      pass "staged canonical directory $directory is present"
    else
      fail "staged canonical directory $directory is missing"
    fi
  done

  for legacy in app poc-chat hermes corpus infra/k8s; do
    if git -C "$REPO_ROOT" ls-files -- "$legacy" | rg -q .; then
      fail "legacy source path remains in staged index: $legacy"
    else
      pass "legacy source path absent from staged index: $legacy"
    fi
  done

  staged_forbidden_pattern='(^|/)(\.codex/|\.env\.poc$|docs/plans/|node_modules/|dist/|coverage/|build/|test-results/|playwright-report/|results/|\.gradle/|.* 2\.)'
  if git -C "$REPO_ROOT" diff --cached --name-only --diff-filter=ACMRT | rg -q "$staged_forbidden_pattern"; then
    fail "staged set contains a local, generated, personal-plan or duplicate path"
  else
    pass "staged set contains no local, generated, personal-plan or duplicate path"
  fi
fi

if ((failures > 0)); then
  printf 'release boundary: %d failure(s)\n' "$failures" >&2
  exit 1
fi

printf 'release boundary: PASS\n'
