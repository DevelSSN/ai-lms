#!/usr/bin/env bash
# restart-orchestrator.sh -- bounce the RQ5 orchestrator automatically.
#
# The orchestrator runs under `mvn quarkus:dev`. This helper finds the dev
# process bound to the orchestrator port, kills its whole process group (the
# launcher is started with setsid; hand-started trees fall back to a recursive
# child kill, never touching the caller's own group), waits for the port to
# free, verifies no stale process still has the dev log open for writing,
# truncates the log, relaunches `quarkus:dev` detached behind nohup+setsid,
# and waits until /q/health answers again. Redis-backed session state is then
# re-read from storage, which is exactly what the RQ5 --restart arm is testing.
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
    -h|--help) sed -n '2,21p' "$0"; exit 0 ;;
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

# True when $1 is this script itself or one of its ancestors: the process
# group of any such pid is (part of) our own lineage and must never be
# group-killed.
pid_is_our_ancestor() {
  local target="$1" p="$$"
  while [[ -n "$p" && "$p" != "0" && "$p" != "1" ]]; do
    [[ "$p" == "$target" ]] && return 0
    p="$(ps -o ppid= -p "$p" 2>/dev/null | tr -d ' ')"
    [[ -z "$p" ]] && return 1
  done
  return 1
}

# Terminate a process and all of its children (children first so nothing lingers).
kill_tree() {
  local pid="$1" sig="$2" child
  for child in $(ps -o pid= --ppid "$pid" 2>/dev/null); do
    kill_tree "$child" "$sig"
  done
  kill -"$sig" "$pid" 2>/dev/null || true
}

# Stop $1 with signal $2 without ever signalling our own process group or any
# ancestor: mvn quarkus:dev is launched in its own group (setsid), so a group
# kill takes out the launcher and every child at once; anything started by hand
# in our own lineage falls back to a recursive tree kill.
OUR_SID="$(ps -o sid= -p $$ | tr -d ' ')"
stop_target() {
  local pid="$1" sig="$2" pgid
  pgid="$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')"
  if [[ -n "$pgid" && "$pgid" != "$OUR_SID" ]] \
      && ! pid_is_our_ancestor "$pgid"; then
    kill -"$sig" -- "-$pgid" 2>/dev/null || true
  else
    kill_tree "$pid" "$sig"
  fi
}

# Print unique PIDs that hold $LOG open for writing. A stale writer keeps
# appending at its old offset after truncation, corrupting the fresh log.
log_writers() {
  local fd fdinfo flags
  for fd in /proc/[0-9]*/fd/*; do
    [[ -e "$fd" ]] || continue
    [[ "$(readlink "$fd" 2>/dev/null)" == "$LOG" ]] || continue
    fdinfo="${fd/\/fd\//\/fdinfo\/}"
    flags="$(awk '/^flags:/ { print $2 }' "$fdinfo" 2>/dev/null || true)"
    # Access mode is the low 3 bits of the octal flags: 1/2 = write or read-write.
    if [[ -z "$flags" || ! "$flags" =~ ^[0-7]+$ ]] || (( 8#$flags & 3 )); then
      echo "${fd#/proc/}" | cut -d/ -f1
    fi
  done | sort -u
}

OLD="$(pid_on_port || true)"
if [[ -n "$OLD" ]]; then
  echo "restart: stopping dev server (pid $OLD on :$PORT)"
  stop_target "$OLD" TERM
  for _ in $(seq 1 60); do
    [[ -z "$(pid_on_port || true)" ]] && break
    sleep 1
  done
  if [[ -n "$(pid_on_port || true)" ]]; then
    echo "restart: graceful stop timed out; sending SIGKILL" >&2
    stop_target "$OLD" KILL
    sleep 2
  fi
else
  echo "restart: nothing listening on :$PORT (already down)"
fi

# Make sure no leftover launcher still writes to the log before truncating it.
holders="$(log_writers | tr '\n' ' ')"
for _ in $(seq 1 15); do
  [[ -z "${holders// /}" ]] && break
  sleep 1
  holders="$(log_writers | tr '\n' ' ')"
done
if [[ -n "${holders// /}" ]]; then
  echo "restart: SIGKILLing stale log writers:$holders" >&2
  for h in $holders; do kill -KILL "$h" 2>/dev/null || true; done
  sleep 1
  holders="$(log_writers | tr '\n' ' ')"
  if [[ -n "${holders// /}" ]]; then
    echo "restart: processes still writing to $LOG:$holders; refusing to truncate" >&2
    exit 1
  fi
fi
: >"$LOG"

echo "restart: relaunching '$MVN quarkus:dev' in $ORCH_DIR (log: $LOG)"
(
  cd "$ORCH_DIR"
  if command -v setsid >/dev/null 2>&1; then
    setsid nohup "$MVN" quarkus:dev >>"$LOG" 2>&1 &
  else
    nohup "$MVN" quarkus:dev >>"$LOG" 2>&1 &
  fi
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
