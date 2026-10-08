#!/usr/bin/env python3
"""Interactive, per-annotator RQ5 judge (Step 3 of the annotation protocol).

Renders every (conv_id, blind-label) pair from rq5-responses.jsonl with the
turn-1/turn-2 learner and system messages, asks for a 1/0 success judgment
per datasets/rq5-rubric.md, and writes the annotator's own score CSV in the
exact shape aggregate.table10 consumes (header  conv_id,label,success).

Blindness is enforced in code: the script never reads rq5-blind-map.json, so
the annotator only ever sees the rotated {x,y,z} labels. rq5-scores-a.csv and
rq5-scores-b.csv are written by independent invocations (--annotator a|b),
each atomic-rewritten after every judgment so progress survives a crash.
Partially-filled sheets (e.g. from make_rq5_score_sheets.py) resume in place;
already-judged pairs are locked and never re-asked.

Usage:
  python3 evaluation/judge_rq5.py --annotator a [--limit N] [--seed 20241001]
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import random
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
RESPONSES = HERE / "rq5-responses.jsonl"
RUBRIC = HERE / "datasets" / "rq5-rubric.md"
COLUMNS = ["conv_id", "label", "success"]
SUBJ = {1: "one", 0: "zero"}  # fallback labels in case int formatting fails


def load_responses() -> list[dict]:
    if not RESPONSES.exists():
        raise SystemExit(f"{RESPONSES} missing - run rq5_memory.py first")
    return [json.loads(l) for l in RESPONSES.read_text().splitlines() if l.strip()]


def load_existing(annotator: str) -> dict[tuple[str, str], int]:
    p = HERE / f"rq5-scores-{annotator}.csv"
    out: dict[tuple[str, str], int] = {}
    if p.exists():
        for r in csv.DictReader(open(p)):
            if r["success"] not in ("0", "1"):
                continue
            out[(r["conv_id"], r["label"])] = int(r["success"])
    return out


def write_scores(annotator: str, scores: dict[tuple[str, str], int]) -> None:
    p = HERE / f"rq5-scores-{annotator}.csv"
    rows = sorted(scores.items(), key=lambda kv: (kv[0][0], kv[0][1]))
    fd, tmp = tempfile.mkstemp(dir=HERE, prefix=".rq5-scores-", suffix=".tmp")
    with os.fdopen(fd, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(COLUMNS)
        w.writerows((cid, lab, v) for (cid, lab), v in rows)
    os.replace(tmp, p)


def print_rubric() -> None:
    if RUBRIC.exists():
        print(RUBRIC.read_text())
    else:
        print(f"(rubric not found at {RUBRIC}; adhere to C1-C3 / F1-F4)")


def ask(prompt: str) -> str:
    while True:
        try:
            v = input(prompt + " [y/n/?/r/q] > ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            return "q"
        if v in ("y", "n", "?", "r", "q"):
            return v
        print("  expected y, n, ? (help), r (rubric) or q (save+quit)")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--annotator", required=True, choices=["a", "b"])
    ap.add_argument("--limit", type=int, default=None, help="judge at most N items (smoke test)")
    ap.add_argument("--seed", default="20241001")
    args = ap.parse_args()

    scores = load_existing(args.annotator)
    rows = load_responses()
    pairs = sorted({(r["conv_id"], r["label"]) for r in rows})
    if not pairs:
        raise SystemExit("no (conv_id,label) pairs in rq5-responses.jsonl")
    todo = [p for p in pairs if p not in scores]
    if len(todo) < len(pairs):
        print(f"resuming: {len(pairs) - len(todo)} already judged, {len(todo)} to go")
    todo = sorted(todo, key=lambda p: (int(str(p[0])[1:]), p[1]))
    rng = random.Random(f"rq5-{args.annotator}-{args.seed}")
    order = rng.sample(todo, len(todo))
    if args.limit:
        order = order[: args.limit]
    if not order:
        print(f"rq5-scores-{args.annotator}.csv already complete ({len(pairs)} judged)")
        return 0

    by = {(r["conv_id"], r["label"]): r for r in rows}
    print_rubric()
    judged = 0
    for i, (cid, label) in enumerate(order, 1):
        r = by[(cid, label)]
        print(f"\n[{judged + 1 + (len(pairs) - len(todo))}/{len(pairs)}] conv {cid}  label {label}")
        print("  -- turn 1 (learner) --")
        print("  " + str(r["turn1_user"]).replace("\n", "\n  "))
        print("  -- turn-1 answer --")
        print("  " + str(r["turn1_system"]).replace("\n", "\n  "))
        print("  -- turn 2 (learner) --")
        print("  " + str(r["turn2_user"]).replace("\n", "\n  "))
        print("  -- turn-2 answer --")
        print("  " + str(r["turn2_system"]).replace("\n", "\n  "))
        while True:
            v = ask("success?")
            if v == "q":
                print(f"saved {len(scores)} judgments (resume later)")
                return 0
            if v == "?":
                print("  y = success, n = failure, r = read rubric, q = save+quit")
                continue
            if v == "r":
                print_rubric()
                continue
            scores[(cid, label)] = 1 if v == "y" else 0
            write_scores(args.annotator, scores)
            judged += 1
            break

    print(f"\nout of {len(order)} presented: {sum(1 for p in order if scores.get(p) == 1)} judged as success")
    print(f"done: rq5-scores-{args.annotator}.csv has {len(scores)}/{len(pairs)} judged "
          "(do not open the other annotator's file until both are finished)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())