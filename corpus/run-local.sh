#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPETITIONS=3
BASE_URL="${POC_BASE_URL:-http://127.0.0.1:8081}"
RESULTS_DIR="${SCRIPT_DIR}/results"
MEMORY_SEED_MODE="${CORPUS_MEMORY_SEED_MODE:-verify-only}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repetitions)
      [[ $# -ge 2 ]] || { echo "--repetitions requires a positive integer" >&2; exit 2; }
      REPETITIONS="$2"
      shift 2
      ;;
    --base-url)
      [[ $# -ge 2 ]] || { echo "--base-url requires a URL" >&2; exit 2; }
      BASE_URL="$2"
      shift 2
      ;;
    --output)
      [[ $# -ge 2 ]] || { echo "--output requires a directory" >&2; exit 2; }
      RESULTS_DIR="$2"
      shift 2
      ;;
    --memory-seed-mode)
      [[ $# -ge 2 ]] || { echo "--memory-seed-mode requires verify-only or setup-events" >&2; exit 2; }
      MEMORY_SEED_MODE="$2"
      shift 2
      ;;
    -h|--help)
      echo "usage: $0 [--repetitions N] [--base-url URL] [--output DIR] [--memory-seed-mode MODE]"
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      exit 2
      ;;
  esac
done

[[ "$REPETITIONS" =~ ^[1-9][0-9]*$ ]] || {
  echo "--repetitions must be a positive integer" >&2
  exit 2
}
command -v ruby >/dev/null 2>&1 || { echo "ruby is required" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 2; }
case "$MEMORY_SEED_MODE" in
  verify-only|setup-events) ;;
  *) echo "--memory-seed-mode must be verify-only or setup-events" >&2; exit 2 ;;
esac

# The POC ingress is bearer-protected. Reuse the already-required local
# internal-tool token without sourcing .env.poc or printing its value.
if [[ -z "${POC_API_TOKEN:-}" && -f "$REPO_ROOT/.env.poc" ]]; then
  POC_API_TOKEN="$(python3 - "$REPO_ROOT/.env.poc" <<'PY'
import sys

for raw_line in open(sys.argv[1], encoding="utf-8"):
    line = raw_line.strip()
    if not line or line.startswith("#"):
        continue
    if line.startswith("export "):
        line = line[7:].lstrip()
    name, separator, value = line.partition("=")
    if separator and name.strip() == "HERMES_INTERNAL_TOOL_TOKEN":
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        print(value)
        break
PY
)"
  export POC_API_TOKEN
fi
[[ -n "${POC_API_TOKEN:-}" ]] || {
  echo "POC_API_TOKEN is required (run with .env.poc or set it explicitly)." >&2
  exit 2
}

shopt -s nullglob
scenarios=("$SCRIPT_DIR"/scenarios/*.yml)
(( ${#scenarios[@]} > 0 )) || { echo "no scenarios found" >&2; exit 2; }
for scenario in "${scenarios[@]}"; do
  ruby "$SCRIPT_DIR/report.rb" validate --scenario "$scenario"
done

mkdir -p "$RESULTS_DIR"
run_dir="$(mktemp -d "${TMPDIR:-/tmp}/urbana-corpus.XXXXXX")"
cleanup() { rm -rf -- "$run_dir"; }
trap cleanup EXIT
records="$run_dir/records.jsonl"
: > "$records"
run_id="$(ruby -rtime -rsecurerandom -e 'puts "run-#{Time.now.utc.strftime("%Y%m%dT%H%M%SZ")}-#{SecureRandom.hex(6)}"')"

failures=0
aggregate_failed=0

for scenario in "${scenarios[@]}"; do
  for (( repetition = 1; repetition <= REPETITIONS; repetition++ )); do
    record="$run_dir/record-${repetition}-$(basename "$scenario" .yml).json"
    if ruby "$SCRIPT_DIR/report.rb" run \
        --scenario "$scenario" \
        --repetition "$repetition" \
        --run-id "$run_id" \
        --base-url "$BASE_URL" \
        --memory-seed-mode "$MEMORY_SEED_MODE" \
        --output "$record"; then
      :
    else
      failures=$((failures + 1))
    fi
    ruby -rjson -e 'puts JSON.generate(JSON.parse(File.read(ARGV.fetch(0))))' "$record" >> "$records"
  done
done

if ruby "$SCRIPT_DIR/report.rb" aggregate \
    --input "$records" \
    --output "$RESULTS_DIR/summary.json"; then
  :
else
  aggregate_status=$?
  aggregate_failed=1
  echo "Corpus aggregate gate failed with status $aggregate_status; see $RESULTS_DIR/summary.json" >&2
fi

if (( failures > 0 || aggregate_failed > 0 )); then
  echo "Corpus completed with $failures execution errors; see $RESULTS_DIR/summary.json" >&2
  exit 1
fi
echo "Corpus completed: $RESULTS_DIR/summary.json"
