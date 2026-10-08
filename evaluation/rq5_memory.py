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
from pathlib import Path

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


def send(message: str, user: str, session: str) -> dict:
    return infra.orchestrate(message, session_id=session, user_id=user)


def run_conv(conv: dict, config: str, user_id: str, session_t1: str, session_t2: str,
             topic_label: str) -> dict:
    t1 = send(conv[1], user_id, session_t1)
    t1_text = t1.get("response") or t1.get("reply") or t1.get("message") or ""
    if config == "c":
        send(PROFILE_UPDATE.format(topic=topic_label), user_id, session_t2)
    t2 = send(conv[2], user_id, session_t2)
    t2_text = t2.get("response") or t2.get("reply") or t2.get("message") or ""
    return {"turn1_user": conv[1], "turn2_user": conv[2],
            "turn1_system": t1_text, "turn2_system": t2_text}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--configs", default="a,b,c")
    ap.add_argument("--sessions", default=None,
                    help="fixed session base e.g. eval-rq5 (default: per-conv random)")
    ap.add_argument("--restart", action="store_true")
    ap.add_argument("--container", default="orchestrator")
    args = ap.parse_args()

    random.seed(20241001)
    convs = load_convs()
    vc = [c for c in args.configs.split(",") if c in LABELS] or list(LABELS)

    responses: list[dict] = []
    raw_rows: list[tuple[str, str, str, str]] = []
    blind_map: dict[str, dict] = {}

    for conv_id in sorted(convs, key=lambda c: int(c[1:])):
        conv = convs[conv_id]
        perm = random.sample(list(vc), len(vc))
        anon = ANON[: len(vc)]
        labels_for_conv = dict(zip(perm, anon))
        base = args.sessions or f"eval-rq5-{conv_id}"
        for config in vc:
            anon_label = labels_for_conv[config]
            t1_session = f"{base}-t1-{config}"
            t2_session = f"{base}-t1-{config}" if config in ("b", "c") else f"{base}-t2-{config}"
            out = run_conv(conv, config, "eval-user-rq5", t1_session, t2_session, conv[1][:80])
            responses.append({"conv_id": conv_id, "label": anon_label, **out})
            raw_rows.append((conv_id, config, out["turn2_system"]))
            time.sleep(0.5)  # pacing: the numbers box is a single CPU box

    RESPONSES.write_text("".join(json.dumps(r) + "\n" for r in responses))
    BLIND_MAP.write_text(json.dumps(blind_map_full(convs, vc), indent=2) + "\n")
    with open(RAW, "w") as fh:
        w = csv.writer(fh)
        w.writerow(["conv_id", "config", "turn2_system"])
        w.writerows(raw_rows)
    print(f"wrote {RESPONSES} ({len(responses)} turns), {BLIND_MAP}, {RAW}")

    if args.restart:
        run_restart_test(convs, args.container)
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


def run_restart_test(convs, container: str) -> None:
    picks = ["c" + str(i) for i in range(6)]  # subset (6 conversations)
    rows = []
    for cid in picks:
        conv = convs[cid]
        user = "eval-user-rq5-restart"
        session = f"eval-rq5-restart-{cid}"
        t1 = send(conv[1], user, session)
        t1_text = t1.get("response") or ""
        print(f"  restart test {cid}: restarting {container} ...")
        r = subprocess.run(["podman", "restart", "-t", "10", container],
                           capture_output=True, text=True, timeout=120)
        if r.returncode != 0:
            raise RuntimeError(f"podman restart failed: {r.stderr.strip()}")
        t2 = send(conv[2], user, session)
        t2_text = t2.get("response") or ""
        rows.append({"conv_id": cid, "restarted_after": "turn1",
                     "turn1_user": conv[1], "turn2_user": conv[2],
                     "turn1_system": t1_text, "turn2_system": t2_text})
        time.sleep(1.0)
    RESTART_RESPONSES.write_text("".join(json.dumps(r) + "\n" for r in rows))
    print(f"wrote {RESTART_RESPONSES}")


if __name__ == "__main__":
    raise SystemExit(main())