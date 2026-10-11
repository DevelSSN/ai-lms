#!/usr/bin/env bash
# Restart helper for the RQ5 --restart arm when the orchestrator runs under
# `mvn quarkus:dev` (manual restart; NOT podman/systemd). rq5_memory.py calls
# this script in place of `podman restart` via:  --restart-helper restart-orchestrator.sh
#
# The script kills the process currently bound to the orchestrator port, waits
# for the port to free, and relaunches quarkus:dev in the orchestrator module.
# The caller (wait_for_orchestrator in rq5_memory.py) then polls /q/health
# before sending turn 2, so Redis window state is restored from storage.
#
# Usage: restart-orchestrator.sh [<$PORT> [<$ORCHESTRATOR_DIR>]]
# Env:  PORT / ORCH_DIR override the defaults below.
set -euo pipefail

PORT="${1:-${PORT:-10082}}"
ORCH_DIR="${2:-${ORCH_DIR:-/home/coe/Documents/ai-lms/orchestrator}}"
LOG="${QUARKUS_DEV_LOG:-/tmp/quarkus-dev.log}"

if [[ ! -d "$ORCH_DIR" ]]; then
  echo "error: orchestrator dir not found: $ORCH_DIR" >&2
  exit 1
fi
if ! command -v ss >/dev/null 2>&1; then
  echo "error: ss is required (iproute2)" >&2
  exit 1
fi

pid_of_port() { ss -ltnp 2>/dev/null | awk -v port=":$PORT" '$4 ~ port { gsub(/.*pid=/, "", $NF); gsub(/,.*/, "", $NF); print $NF }' | head -1; }

OLD=$(pid_of_port || true)
if [[ -n "$OLD" ]]; then
  echo "restart: killing PID $OLD on :$PORT"
  kill -TERM "$OLD"
  for _ in $(seq 1 60); do
    if [[ -z "$(pid_of_port || true)" ]]; then break; fi
    sleep 1
  done
  if [[ -n "$(pid_of_port || true)" ]]; then
    echo "error: :$PORT still busy after graceful shutdown; refusing force-kill" >&2
    exit 1
  fi
else
  echo "restart: nothing listening on :$PORT (already down)"
fi

echo "restart: relaunching quarkus:dev in $ORCH_DIR (log: $LOG)"
cd "$ORCH_DIR"
nohup ./mvnw quarkus:dev -q >>"$LOG" 2>&1 &
echo "restart: launched PID $!; caller will poll /q/health"