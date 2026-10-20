#!/usr/bin/env bash
# run-rqs.sh -- interactive driver for the AI-LMS evaluation sweeps (RQ1-RQ5).
#
# Reconstructs the runbook that eval.md used to point at (test.md is gitignored
# and gone): every step prints the exact command, what it refreshes, and how
# long it takes, runs it with output tee'd to evaluation/logs/, and waits for
# your "ok" before moving on. Steps are resume-friendly: re-running the script
# keeps partial progress for the sweeps that support it (rq1_sweep,
# baseline_sweep, rq5_memory --restart).
#
# This script never starts anything by itself. Infra, Ollama and the
# orchestrator are yours to start; the driver only verifies and tells you the
# command when something is red.
#
# Order: preflight -> RQ2 -> RQ1 -> RQ3 -> RQ4 -> RQ5 -> aggregate
#   (RQ2 is infra-only and gives a fresh retrieval baseline; RQ4 reuses the
#    heldout predictions produced inside RQ1, so RQ1 must precede it.)
#
# Usage:
#   evaluation/run-rqs.sh                 # everything, prompted
#   evaluation/run-rqs.sh --list          # show the step list and exit
#   evaluation/run-rqs.sh --only rq2,rq1  # those steps only (preflight still runs)
#   evaluation/run-rqs.sh --from rq3      # start at RQ3, run to the end
#   evaluation/run-rqs.sh --fresh         # pass --fresh to the resume-capable sweeps
#   evaluation/run-rqs.sh --dry-run       # print what would run, run nothing
#   evaluation/run-rqs.sh --auto          # no prompts; a red check only warns
#
# Answers at every prompt: ok | skip | retry | quit
set -uo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd -- "$HERE/.." && pwd)"
INFRA_DIR="$REPO/infra"
LOGDIR="$HERE/logs"
PYTHON="${PYTHON:-python3}"

HEALTH="http://localhost:10082/q/health"
ORCHESTRATE="http://localhost:10082/api/v1/orchestrate"
OLLAMA="http://localhost:11434"

ALL_STEPS=(rq2 rq1 rq3 rq4 rq5 aggregate)

ONLY=""
FROM=""
FRESH=0
DRY=0
AUTO=0

usage() { sed -n '2,28p' "$0"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --list) printf '%s\n' "${ALL_STEPS[@]}"; exit 0 ;;
    --only) ONLY="$2"; shift 2 ;;
    --from) FROM="$2"; shift 2 ;;
    --fresh) FRESH=1; shift ;;
    --dry-run) DRY=1; shift ;;
    --auto) AUTO=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

# ---------------------------------------------------------------------------
# step selection
# ---------------------------------------------------------------------------
SELECTED=()
if [[ -n "$ONLY" ]]; then
  IFS=',' read -r -a wanted <<<"$ONLY"
  for id in "${ALL_STEPS[@]}"; do
    for w in "${wanted[@]}"; do
      [[ "$id" == "$w" ]] && SELECTED+=("$id")
    done
  done
  if [[ ${#SELECTED[@]} -eq 0 ]]; then
    echo "--only matched nothing; valid: ${ALL_STEPS[*]}" >&2
    exit 2
  fi
elif [[ -n "$FROM" ]]; then
  start=-1
  for i in "${!ALL_STEPS[@]}"; do [[ "${ALL_STEPS[$i]}" == "$FROM" ]] && start=$i; done
  if [[ $start -lt 0 ]]; then
    echo "--from '$FROM' is not a step; valid: ${ALL_STEPS[*]}" >&2
    exit 2
  fi
  SELECTED=("${ALL_STEPS[@]:$start}")
else
  SELECTED=("${ALL_STEPS[@]}")
fi

FRESH_ARGS=()
[[ $FRESH == 1 ]] && FRESH_ARGS=(--fresh)

mkdir -p "$LOGDIR"

trap 'echo; echo "interrupted."; exit 130' INT

# ---------------------------------------------------------------------------
# small helpers
# ---------------------------------------------------------------------------
banner() {
  local sub="${2:-}"
  printf '\n'
  printf '============================================================\n'
  printf '  %s\n' "$1"
  printf '============================================================\n'
  printf '%s\n\n' "$sub"
}

cmd_str() { printf '%q ' "$@"; }

ask() {  # 0=ok 1=skip 2=retry 3=quit
  local p="$1" a
  while true; do
    printf '%s (ok/skip/retry/quit): ' "$p"
    IFS= read -r a || { echo; echo "EOF -> quit"; exit 130; }
    case "$a" in
      ok|OK|Ok|y|Y|yes|YES) return 0 ;;
      s|S|skip|SKIP) return 1 ;;
      r|R|retry|RETRY) return 2 ;;
      q|Q|quit|QUIT) echo "quit."; exit 0 ;;
      *) echo "  answer with: ok | skip | retry | quit" ;;
    esac
  done
}

tcp_ok() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

wait_green() {  # $1 = check function, $2 = label, $3 = fix command
  local check="$1" label="$2" fix="$3"
  while true; do
    if "$check"; then
      echo "  OK   $label"
      return 0
    fi
    echo "  RED  $label"
    echo "  fix: $fix"
    [[ $AUTO == 1 ]] && { echo "  (--auto: continuing anyway)"; return 1; }
    local rc=0
    ask "run that, then let me re-check" || rc=$?
    case $rc in
      1) echo "  (skipped check: $label)"; return 1 ;;
      3) exit 0 ;;
    esac
  done
}

run_cmd() {  # $1 = step id, then command words. Returns 0, or 1 if skipped.
  local id="$1" title="$2" st=0 lf
  shift 2
  banner "[$id] $title" "cmd: $(cmd_str "$@")"
  if [[ $DRY == 1 ]]; then
    echo "[dry-run] would run the command above"
    return 0
  fi
  if [[ $AUTO != 1 ]]; then
    ask "run it" || { echo "  [skip] $id"; return 1; }
  fi
  while :; do
    lf="$LOGDIR/$(date +%Y%m%d-%H%M%S)-$id.log"
    echo "log: $lf"
    "$@" 2>&1 | tee "$lf"
    st=${PIPESTATUS[0]}
    printf '\n[%s] exit=%s\n' "$id" "$st"
    [[ $AUTO == 1 ]] && return "$st"
    local rc=0
    ask "next?" || rc=$?
    case $rc in
      0) return 0 ;;
      1) echo "  [skip] $id"; return 1 ;;
      2) continue ;;
      3) exit 0 ;;
    esac
  done
}

# ---------------------------------------------------------------------------
# preflight
# ---------------------------------------------------------------------------
check_infra() {
  tcp_ok 7432 && tcp_ok 6379 && tcp_ok 10633 && tcp_ok 19000 && tcp_ok 10081
}
check_ollama() {
  "$PYTHON" - "$OLLAMA" <<'PY' 2>/dev/null
import sys, json, urllib.request
base = sys.argv[1]
try:
    d = json.load(urllib.request.urlopen(base + "/api/tags", timeout=4))
except Exception:
    sys.exit(1)
names = [m.get("name", "") for m in d.get("models", [])]
need = ["llama3.2:3b", "llama3.2:1b", "nomic-embed-text"]
sys.exit(0 if all(any(n.startswith(k) for n in names) for k in need) else 1)
PY
}
check_orchestrator() { curl -fsS -m 3 "$HEALTH" >/dev/null 2>&1; }
check_corpus() {
  local n
  n="$(podman exec postgres psql -U ailms -d ailms -tAc \
        "SELECT count(*) FROM content_documents WHERE status='INDEXED';" 2>/dev/null | tr -d '[:space:]')"
  [[ -n "$n" && "$n" -gt 0 && -f "$HERE/datasets/corpus-manifest.json" ]]
}

preflight() {
  banner "[preflight] environment" \
"Nothing is started for you. Each check below prints the exact command to
fix it; press ok only once you have actually run it, then let it re-check.
Skipping a red check carries it into the sweeps as a failure."
  if [[ $DRY == 1 ]]; then
    echo "[dry-run] checks: infra ports, ollama models, orchestrator health, corpus"
    return 0
  fi
  wait_green check_infra \
    "infra (pg :7432, redis :6379, qdrant :10633, minio :19000, keycloak :10081)" \
    "cd $INFRA_DIR && podman compose up -d postgres redis kafka keycloak qdrant minio"
  wait_green check_ollama \
    "ollama :11434 has llama3.2:3b, llama3.2:1b, nomic-embed-text" \
    "start ollama yourself (you keep it), then: curl -s localhost:11434/api/tags"
  wait_green check_orchestrator \
    "orchestrator dev server :10082 healthy" \
    "$HERE/restart-orchestrator.sh"
  wait_green check_corpus \
    "eval corpus ingested (content_documents INDEXED > 0, manifest present)" \
    "$PYTHON $HERE/ingest_corpus.py"
}

# ---------------------------------------------------------------------------
# steps
# ---------------------------------------------------------------------------
step_rq2() {
  run_cmd rq2 "RQ2 retrieval quality -> rq2.json, rq2-query-results.csv" \
    "$PYTHON" "$HERE/rq2_retrieval.py" || return
}

step_rq1() {
  banner "[rq1] intent routing (Table 6) - 3 arms" \
"Each POST is a fresh user/session, so a partial sweep resumes cleanly.
Only run the compile step once its sweep prints SWEEP COMPLETE: compile_rq1
aborts on row-count mismatch."
  run_cmd rq1-hybrid "arm 1/3 hybrid: 1200-utterance sweep" \
    "$PYTHON" "$HERE/rq1_sweep.py" "${FRESH_ARGS[@]+"${FRESH_ARGS[@]}"}" || return
  run_cmd rq1-hybrid-compile "arm 1/3 compile -> rq1-metrics.json" \
    "$PYTHON" "$HERE/compile_rq1.py" || return
  run_cmd rq1-monolithic "arm 2/3 monolithic: bypassRoutes=true sweep" \
    "$PYTHON" "$HERE/baseline_sweep.py" "${FRESH_ARGS[@]+"${FRESH_ARGS[@]}"}" || return
  run_cmd rq1-monolithic-compile "arm 2/3 compile -> rq1-monolithic-metrics.json" \
    "$PYTHON" "$HERE/compile_rq1.py" --monolithic || return
  run_cmd rq1-probe "arm 3/3 heldout 218 hand-written sweep" \
    "$PYTHON" "$HERE/rq1_sweep.py" --corpus probe-novel/heldout-218.csv \
    --out-prefix probe-218 "${FRESH_ARGS[@]+"${FRESH_ARGS[@]}"}" || return
  run_cmd rq1-probe-compile "arm 3/3 compile -> probe-218-metrics.json (feeds RQ4)" \
    "$PYTHON" "$HERE/compile_rq1.py" --probe
}

step_rq3() {
  banner "[rq3] latency (Table 8)" \
"Three passes: TTFT (raw ollama streaming), end-to-end /api/v1/orchestrate
round-trips, and sustained throughput at 1/2/4/8 in-flight (90s window each)."
  run_cmd rq3 "RQ3 -> rq3.json + rq3-*.csv" \
    "$PYTHON" "$HERE/rq3_latency.py"
}

step_rq4() {
  banner "[rq4] ablations (Table 9)" \
"Chunk arms 400/50 and 1600/200 re-embed the 11-PDF corpus (slow); the
zero-shot arm makes 218 llama3.2:3b calls. Few-shot and deterministic rows
reuse the RQ1 outputs, so run rq1 first. Needs datasets/qrels-a.jsonl + qrels-b."
  run_cmd rq4 "RQ4 -> rq4.json" \
    "$PYTHON" "$HERE/rq4_ablations.py"
}

step_rq5() {
  banner "[rq5] cross-session memory (Table 10)" \
"60 two-turn conversations under configs a/b/c, then the 6-conversation
restart arm, which bounces the orchestrator through restart-orchestrator.sh
between turns. Scoring is HUMAN: two annotators judge the blind sheets."
  run_cmd rq5-memory "memory sweep -> rq5-responses.jsonl + rq5-blind-map.json" \
    "$PYTHON" "$HERE/rq5_memory.py" --configs a,b,c "${FRESH_ARGS[@]+"${FRESH_ARGS[@]}"}" || return
  run_cmd rq5-restart "restart-recovery arm -> rq5-restart.jsonl (+ .csv)" \
    "$PYTHON" "$HERE/rq5_memory.py" --restart "${FRESH_ARGS[@]+"${FRESH_ARGS[@]}"}" || return
  run_cmd rq5-sheets "blind score sheets -> rq5-scores-a.csv, rq5-scores-b.csv" \
    "$PYTHON" "$HERE/make_rq5_score_sheets.py" || return
  if [[ $DRY == 1 ]]; then
    echo "[dry-run] would pause here for two annotators"
    return 0
  fi
  banner "[rq5] annotation" \
"Two independent annotators fill the blind sheets, judging against
$HERE/datasets/rq5-rubric.md (1 = continuation success, 0 = failure).
Each annotator runs their own interactive judge; progress is saved after
every judgment, so a sheet can be resumed. Use a DIFFERENT person for b.

    $PYTHON $HERE/judge_rq5.py --annotator a
    $PYTHON $HERE/judge_rq5.py --annotator b

Both sheets must be complete (no blank success column) before aggregate."
  [[ $AUTO == 1 ]] && return 0
  local rc=0
  ask "both annotators are done" || rc=$?
  case $rc in
    1) echo "  [skip] rq5 annotation" ;;
    3) exit 0 ;;
  esac
}

step_aggregate() {
  banner "[aggregate] Tables 7-10 -> rq2.md .. rq5.md" \
"aggregate.py refuses to invent cells: anything missing prints [PENDING], and
--require-scores demands the two RQ5 score sheets. Diff the printed tables
against sle-paper.tex by hand; this driver never edits the paper."
  run_cmd aggregate "aggregate -> rq{2..5}.md" \
    "$PYTHON" "$HERE/aggregate.py" --require-scores || return
  [[ $DRY == 1 ]] && return 0
  echo "produced:"
  for f in rq2.md rq3.md rq4.md rq5.md; do
    [[ -f "$HERE/$f" ]] && echo "  evaluation/$f"
  done
  echo "paper tables to diff: sle-paper.tex (tab:rq2 .. tab:rq5)"
}

# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
echo "run-rqs: steps = ${SELECTED[*]}"
echo "  fresh=${FRESH} dry-run=${DRY} auto=${AUTO}  logs -> $LOGDIR"

preflight || true

for id in "${SELECTED[@]}"; do
  case "$id" in
    rq2) step_rq2 ;;
    rq1) step_rq1 ;;
    rq3) step_rq3 ;;
    rq4) step_rq4 ;;
    rq5) step_rq5 ;;
    aggregate) step_aggregate ;;
    *) echo "unknown step: $id" >&2; exit 2 ;;
  esac
done

banner "[done] finished the selected steps"
echo "Re-run without --fresh to resume any incomplete sweep."
