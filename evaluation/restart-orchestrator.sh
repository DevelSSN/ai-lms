#!/usr/bin/env bash
# restart-orchestrator.sh -- bounce the RQ5 orchestrator automatically.
#
# The orchestrator runs under `mvn quarkus:dev`. This helper finds the dev
# process bound to the orchestrator port, stops it, waits for the port to
# free, relaunches `quarkus:dev` detached behind nohup, and waits until
# /q/health answers again. Redis-backed session state is then re-read from
# storage, which is exactly what the RQ5 --restart arm is testing.
#
# Two ways it is used:
#   1. rq5_memory.py --restart calls it once per bounce (no --continue); the
#      caller then replays turn 2 on the same session.
#   2. After a Ctrl-C you can run it by hand to bounce AND resume the
#      interrupted run:
#          evaluation/restart-orchestrator.sh --continue
#
# Usage: restart-orchestrator.sh [--continue] [--port N] [--dir DIR]
# Env overrides: PORT, ORCH_DIR, MVN, QUARKUS_DEV_LOG, PYTHON, HEALTH_TIMEOUT
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

PORT="${PORT:-10082}"
ORCH_DIR="${ORCH_DIR:-$REPO_DIR/orchestrator}"
MVN="${MVN:-mvn}"
LOG="${QUARKUS_DEV_LOG:-/tmp/quarkus-dev.log}"
PYTHON="${PYTHON:-python3}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"
CONTINUE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --continue) CONTINUE=1; shift ;;
    --port) PORT="$2"; shift 2 ;;
    --dir) ORCH_DIR="$2"; shift 2 ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    *) echo "restart: unknown argument: $1" >&2; exit 2 ;;
  esac
done

HEALTH="http://localhost:${PORT}/q/health"

command -v ss >/dev/null 2>&1 || { echo "restart: 'ss' (iproute2) is required" >&2; exit 1; }
[[ -d "$ORCH_DIR" ]] || { echo "restart: orchestrator dir not found: $ORCH_DIR" >&2; exit 1; }

# Prefer the Maven wrapper beside the module, else the one at the repo root.
if [[ -x "$ORCH_DIR/mvnw" ]]; then MVN="$ORCH_DIR/mvnw"
elif [[ -x "$REPO_DIR/mvnw" ]]; then MVN="$REPO_DIR/mvnw"; fi

pid_on_port() {
  ss -ltnp 2>/dev/null \
    | awk -v port=":$PORT" '$4 ~ port && match($0, /pid=[0-9]+/) { print substr($0, RSTART+4, RLENGTH-4); exit }'
}

OLD="$(pid_on_port || true)"
if [[ -n "$OLD" ]]; then
  echo "restart: stopping dev server (pid $OLD on :$PORT)"
  kill -TERM "$OLD" 2>/dev/null || true
  for _ in $(seq 1 60); do
    [[ -z "$(pid_on_port || true)" ]] && break
    sleep 1
  done
  if [[ -n "$(pid_on_port || true)" ]]; then
    echo "restart: graceful stop timed out; sending SIGKILL" >&2
    kill -KILL "$OLD" 2>/dev/null || true
    sleep 2
  fi
else
  echo "restart: nothing listening on :$PORT (already down)"
fi

echo "restart: relaunching '$MVN quarkus:dev' in $ORCH_DIR (log: $LOG)"
(
  cd "$ORCH_DIR"
  nohup "$MVN" quarkus:dev >"$LOG" 2>&1 &
  echo $! >/tmp/rq5-orchestrator.pid
)
echo "restart: launched; waiting for $HEALTH to become healthy ..."

healthy=0
for _ in $(seq 1 "$HEALTH_TIMEOUT"); do
  if command -v curl >/dev/null 2>&1; then
    if curl -fsS "$HEALTH" >/dev/null 2>&1; then healthy=1; break; fi
  elif [[ -n "$(pid_on_port || true)" ]]; then
    healthy=1; break
  fi
  sleep 1
done
if [[ "$healthy" != "1" ]]; then
  echo "restart: health check timed out after ${HEALTH_TIMEOUT}s (see $LOG)" >&2
  exit 1
fi
echo "restart: healthy again"

if [[ "$CONTINUE" == "1" ]]; then
  echo "restart: resuming the interrupted RQ5 restart arm (--restart, auto-resume)"
  exec "$PYTHON" "$SCRIPT_DIR/rq5_memory.py" --restart
fi
