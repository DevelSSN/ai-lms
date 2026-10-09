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
orchestrator container is restarted (Redis window survives) and turn 2 is sent
again; whether the continuation is still resolved after the restart is judged
the same way. This is the "restart recovery" row of Table 10.

Usage:
  python3 evaluation/rq5_memory.py [--configs a,b,c] [--restart] [--container <name>]
"""
from __future__ import annotations

import argparse
import csv
import json
import random
import subprocess
import sys
import time
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


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--configs", default="a,b,c")
    ap.add_argument("--sessions", default=None,
                    help="fixed session base e.g. eval-rq5 (default: per-conv random)")
    ap.add_argument("--timeout", type=int, default=600,
                    help="per-request timeout in seconds (default: %(default)s)")
    ap.add_argument("--resume", action="store_true",
                    help="skip convs already present in rq5-responses.jsonl")
    ap.add_argument("--restart", action="store_true")
    ap.add_argument("--container", default="orchestrator")
    ap.add_argument("--restart-helper", default=None,
                    help="script to restart the orchestrator (default: podman restart --container)")
    args = ap.parse_args()

    random.seed(20241001)
    convs = load_convs()
    vc = [c for c in args.configs.split(",") if c in LABELS] or list(LABELS)

    if not args.resume:
        for p in (RESPONSES, RAW, RESTART_RESPONSES):
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
                base = args.sessions or f"eval-rq5-{conv_id}"
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

    if args.restart:
        restart_cmd = [args.restart_helper] if args.restart_helper \
            else ["podman", "restart", "-t", "10", args.container]
        run_restart_test(convs, restart_cmd, args.timeout)
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


def run_restart_test(convs, restart_cmd, timeout: int) -> None:
    picks = ["c" + str(i) for i in range(6)]  # subset (6 conversations)
    rows = []
    for cid in picks:
        if cid not in convs:
            raise ValueError(f"restart-pick conv {cid!r} missing from {MULTITURN}")
        conv = convs[cid]
        user = f"eval-user-rq5-restart-{cid}"
        session = f"eval-rq5-restart-{cid}"
        t1 = send(conv[1], user, session, timeout)
        t1_text = t1.get("response") or ""
        print(f"  restart test {cid}: restarting orchestrator ...")
        r = subprocess.run(restart_cmd, capture_output=True, text=True, timeout=120)
        if r.returncode != 0:
            raise RuntimeError(f"restart failed: {r.stderr.strip()}")
        wait_for_orchestrator()
        t2 = send(conv[2], user, session, timeout)
        t2_text = t2.get("response") or ""
        rows.append({"conv_id": cid, "restarted_after": "turn1",
                     "turn1_user": conv[1], "turn2_user": conv[2],
                     "turn1_system": t1_text, "turn2_system": t2_text})
        time.sleep(1.0)
    RESTART_RESPONSES.write_text("".join(json.dumps(r) + "\n" for r in rows))
    print(f"wrote {RESTART_RESPONSES}")


if __name__ == "__main__":
    raise SystemExit(main())