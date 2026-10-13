#!/usr/bin/env python3
"""RQ5 cross-session memory sweep -> rq5-responses.jsonl (+ raw CSV).

For each of the 60 conversations in datasets/rq5-multiturn.jsonl, the deployed
orchestrator is driven through turn 1 then the continuation (turn 2) under three
configurations:

  (a) retain none   — turn 2 runs in a FRESH session, so no cross-turn state
  (b) window        — both turns share one session (default 20-message Redis
                      sliding window carries the turn-1 exchange)
  (c) window+prof   — (b) PLUS an explicit profile update after turn 1, so the
                      recorded learner profile attribute is available as well

Responses are stored anonymously: per conversation the config labels are ROTATED
into {x,y,z} in rq5-responses.jsonl, with the actual mapping held separately in
rq5-blind-map.json so annotators can judge blind (per rq5-rubric.md) and the
scores are unblinded only in aggregate.py.

Restart test (--restart): 6 conversations, config (b); after turn 1 the
orchestrator is restarted BY HAND -- it runs under `mvn quarkus:dev`, so the
operator stops the dev process and starts it again (no script, no podman). The
script watches /q/health go down and then come back, replays turn 2 on the SAME
session so the Redis window has to survive the bounce, and records whether the
continuation still resolves. This is the "restart recovery" row of Table 10.
Outputs are written as rq5-restart.jsonl and rq5-restart.csv.

Usage:
  python3 evaluation/rq5_memory.py [--configs a,b,c]
  python3 evaluation/rq5_memory.py --restart
"""
from __future__ import annotations

import argparse
import csv
import json
import random
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit

import infra

HERE = Path(__file__).resolve().parent
DS = HERE / "datasets"
MULTITURN = DS / "rq5-multiturn.jsonl"
RESPONSES = HERE / "rq5-responses.jsonl"
BLIND_MAP = HERE / "rq5-blind-map.json"
RAW = HERE / "rq5-raw.csv"
RESTART_RESPONSES = HERE / "rq5-restart.jsonl"
RESTART_CSV = HERE / "rq5-restart.csv"
LABELS = ("a", "b", "c")
ANON = ("x", "y", "z")
PROFILE_UPDATE = "Update my profile: our current learning topic is {topic}."


def load_convs() -> dict[str, dict]:
    rows = [json.loads(l) for l in MULTITURN.read_text().splitlines() if l.strip()]
    convs: dict[str, dict] = {}
    for r in rows:
        convs.setdefault(r["conv_id"], {})[r["turn"]] = r["user"]
    return convs


def send(message: str, user: str, session: str, timeout: int) -> dict:
    try:
        return infra.orchestrate(message, session_id=session, user_id=user, timeout=timeout)
    except TimeoutError as exc:
        raise RuntimeError(
            f"orchestrator request timed out after {timeout}s (session={session})") from exc
    except urllib.error.HTTPError as exc:
        hint = (" A 403 means the session id is already owned by a different "
                "user id (stale sweep data) - re-run with a fresh --sessions "
                "namespace") if exc.code == 403 else ""
        raise RuntimeError(
            f"orchestrator returned HTTP {exc.code} for session={session}{hint}") from exc


def run_conv(conv: dict, config: str, user_id: str, session_t1: str, session_t2: str,
             topic_label: str, timeout: int) -> dict:
    t1 = send(conv[1], user_id, session_t1, timeout)
    t1_text = t1.get("response") or t1.get("reply") or t1.get("message") or ""
    if config == "c":
        send(PROFILE_UPDATE.format(topic=topic_label), user_id, session_t2, timeout)
    t2 = send(conv[2], user_id, session_t2, timeout)
    t2_text = t2.get("response") or t2.get("reply") or t2.get("message") or ""
    return {"turn1_user": conv[1], "turn2_user": conv[2],
            "turn1_system": t1_text, "turn2_system": t2_text}


def completed_set() -> set[tuple[str, str]]:
    out = set()
    if not RESPONSES.exists():
        return out
    for line in RESPONSES.read_text().splitlines():
        if not line.strip():
            continue
        r = json.loads(line)
        out.add((r["conv_id"], r["label"]))
    return out


def wait_for_orchestrator(timeout_s: int = 180, poll_s: float = 3.0) -> None:
    base = urlsplit(infra.ORCHESTRATE)
    health = f"{base.scheme}://{base.netloc}/q/health"
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(health, timeout=2) as resp:
                if resp.status == 200:
                    return
        except Exception:
            pass
        time.sleep(poll_s)
    raise RuntimeError(f"orchestrator not healthy at {health}")


def wait_for_orchestrator_down(timeout_s: int = 300, poll_s: float = 1.0) -> None:
    """Block until /q/health stops answering 200 (operator stopped the dev run)."""
    base = urlsplit(infra.ORCHESTRATE)
    health = f"{base.scheme}://{base.netloc}/q/health"
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        up = False
        try:
            with urllib.request.urlopen(health, timeout=2) as resp:
                up = resp.status == 200
        except Exception:
            up = False
        if not up:
            return
        time.sleep(poll_s)
    raise RuntimeError(
        f"orchestrator never went down at {health} within {timeout_s}s; "
        "stop the `mvn quarkus:dev` process by hand and re-run")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--configs", default="a,b,c")
    ap.add_argument("--sessions", default=None,
                    help="fixed session base e.g. eval-rq5 (default: per-conv random)")
    ap.add_argument("--timeout", type=int, default=600,
                    help="per-request timeout in seconds (default: %(default)s)")
    ap.add_argument("--resume", action="store_true",
                    help="skip convs already present in rq5-responses.jsonl")
    ap.add_argument("--restart", action="store_true",
                    help="run ONLY the manual-restart arm (config b) instead of the sweep")
    args = ap.parse_args()

    random.seed(20241001)
    convs = load_convs()

    if args.restart:
        if not args.resume:
            for p in (RESTART_RESPONSES, RESTART_CSV):
                p.unlink(missing_ok=True)
        run_restart_test(convs, args.timeout)
        return 0

    vc = [c for c in args.configs.split(",") if c in LABELS] or list(LABELS)

    if not args.resume:
        for p in (RESPONSES, RAW):
            p.unlink(missing_ok=True)
    done = completed_set() if args.resume else set()

    with open(RESPONSES, "a") as responses_file, open(RAW, "a", newline="") as raw_file:
        raw_w = csv.writer(raw_file)
        if raw_file.tell() == 0:
            raw_w.writerow(["conv_id", "config", "turn2_system"])
        try:
            for conv_id in sorted(convs, key=lambda c: int(c[1:])):
                conv = convs[conv_id]
                perm = random.sample(list(vc), len(vc))
                anon = ANON[: len(vc)]
                labels_for_conv = dict(zip(perm, anon))
                base = args.sessions or f"eval-rq5-v2-{conv_id}"
                for config in vc:
                    anon_label = labels_for_conv[config]
                    if (conv_id, anon_label) in done:
                        print(f"  {conv_id}/{config}: already recorded, skipping")
                        continue
                    t1_session = f"{base}-t1-{config}"
                    t2_session = f"{base}-t1-{config}" if config in ("b", "c") else f"{base}-t2-{config}"
                    user_id = f"eval-user-rq5-{conv_id.lower()}-{config}"
                    out = run_conv(conv, config, user_id, t1_session, t2_session,
                                   conv[1][:80], args.timeout)
                    responses_file.write(json.dumps({"conv_id": conv_id, "label": anon_label, **out}) + "\n")
                    responses_file.flush()
                    raw_w.writerow((conv_id, config, out["turn2_system"]))
                    raw_file.flush()
                    time.sleep(0.5)  # pacing: the numbers box is a single CPU box
        except Exception:
            print(f"\npartial results kept in {RESPONSES}; fix state and re-run with --resume",
                  file=sys.stderr)
            raise

    BLIND_MAP.write_text(json.dumps(blind_map_full(convs, vc), indent=2) + "\n")
    n = len(RESPONSES.read_text().splitlines())
    print(f"wrote {RESPONSES} ({n} turns), {BLIND_MAP}, {RAW}")
    return 0


def blind_map_full(convs, vc):
    """Deterministic map so unblinding survives a process restart — recomputed
    with the same seed/permutation logic used above."""
    random.seed(20241001)
    out = {}
    for conv_id in sorted(convs, key=lambda c: int(c[1:])):
        perm = random.sample(list(vc), len(vc))
        out[conv_id] = dict(zip(perm, ANON[: len(vc)]))
    return out


def run_restart_test(convs, timeout: int) -> None:
    """Manual-restart arm. The orchestrator runs under `mvn quarkus:dev`, so the
    operator stops the dev process and starts it again; the script only watches
    /q/health fall and recover, then replays turn 2 on the same session."""
    picks = ["c" + str(i) for i in range(6)]  # subset (6 conversations)
    rows = []
    for cid in picks:
        if cid not in convs:
            raise ValueError(f"restart-pick conv {cid!r} missing from {MULTITURN}")
        conv = convs[cid]
        user = f"eval-user-rq5-restart-{cid}"
        session = f"eval-rq5-v2-restart-{cid}"
        t1 = send(conv[1], user, session, timeout)
        t1_text = t1.get("response") or t1.get("reply") or t1.get("message") or ""
        print(f"  restart test {cid}: turn 1 sent (session={session}).")
        manual_restart(cid)
        t2 = send(conv[2], user, session, timeout)
        t2_text = t2.get("response") or t2.get("reply") or t2.get("message") or ""
        rows.append({"conv_id": cid, "restarted_after": "turn1",
                     "turn1_user": conv[1], "turn2_user": conv[2],
                     "turn1_system": t1_text, "turn2_system": t2_text})
        time.sleep(1.0)
    RESTART_RESPONSES.write_text("".join(json.dumps(r) + "\n" for r in rows))
    with open(RESTART_CSV, "w", newline="") as raw_file:
        raw_w = csv.writer(raw_file)
        raw_w.writerow(["conv_id", "restarted_after", "turn1_system", "turn2_system"])
        for r in rows:
            raw_w.writerow([r["conv_id"], r["restarted_after"],
                            r["turn1_system"], r["turn2_system"]])
    print(f"wrote {RESTART_RESPONSES}, {RESTART_CSV}")


def manual_restart(cid: str) -> None:
    """Pause for a hand-driven bounce of the `mvn quarkus:dev` orchestrator."""
    if not sys.stdin.isatty():
        raise RuntimeError(
            "manual restart needs an interactive terminal; re-run "
            "`python3 evaluation/rq5_memory.py --restart` from a TTY")
    print(f"    [{cid}] restart the orchestrator BY HAND now:")
    print("      - stop the running `mvn quarkus:dev` process (Ctrl-C)")
    print("      - start it again:  mvn quarkus:dev")
    print("    waiting for the dev server to go down ...")
    wait_for_orchestrator_down()
    print("    ... down; waiting for it to come back healthy ...")
    wait_for_orchestrator(timeout_s=300)
    print(f"    [{cid}] healthy again; replaying turn 2 on the same session")


if __name__ == "__main__":
    raise SystemExit(main())