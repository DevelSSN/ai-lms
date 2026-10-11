#!/usr/bin/env python3
"""Consensus adjudication pass over RQ5 scorer disagreements (Step 4).

Both annotators review, together, every pair on which judge_rq5.py
produced different labels. Responses are shown WITHOUT the config label
(the blind map is never read here), so the consensus vote is blind to the
memory condition. Every pair from rq5-responses.jsonl ends up in
rq5-scores-final.csv: agreed pairs keep their shared value, resolved
disagreements take the consensus vote. aggregate.table10 prefers the final
sheet for the per-config continuation success rate, while the raw a/b
sheets still feed the inter-annotator kappa row.

Usage:
  python3 evaluation/adjudicate_rq5.py [--limit N] [--seed 20241001]
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
FINAL = HERE / "rq5-scores-final.csv"
COLUMNS = ["conv_id", "label", "success"]


def load_sheet(p: Path) -> dict[tuple[str, str], int]:
    out: dict[tuple[str, str], int] = {}
    if p.exists():
        for r in csv.DictReader(open(p)):
            if r["success"] not in ("0", "1"):
                continue
            out[(r["conv_id"], r["label"])] = int(r["success"])
    return out


def write_final(scores: dict[tuple[str, str], int]) -> None:
    rows = sorted(scores.items(), key=lambda kv: (int(str(kv[0][0])[1:]), kv[0][1]))
    fd, tmp = tempfile.mkstemp(dir=HERE, prefix=".rq5-scores-final-", suffix=".tmp")
    with os.fdopen(fd, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(COLUMNS)
        w.writerows((cid, lab, v) for (cid, lab), v in rows)
    os.replace(tmp, FINAL)


def print_rubric() -> None:
    if RUBRIC.exists():
        print(RUBRIC.read_text())
    else:
        print("(rubric not found; adhere to C1-C3 / F1-F4)")


def ask(prompt: str) -> str:
    while True:
        try:
            v = input(prompt + " [y/n/?/r/s/q] > ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            return "q"
        if v in ("y", "n", "?", "r", "s", "q"):
            return v
        print("  expected y, n, ? (help), r (rubric), s (skip) or q (save+quit)")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=None, help="adjudicate at most N items (smoke test)")
    ap.add_argument("--seed", default="20241001")
    args = ap.parse_args()

    if not RESPONSES.exists():
        raise SystemExit(f"{RESPONSES} missing - run rq5_memory.py first")
    a = load_sheet(HERE / "rq5-scores-a.csv")
    b = load_sheet(HERE / "rq5-scores-b.csv")
    if not a or not b:
        raise SystemExit("both rq5-scores-a.csv and rq5-scores-b.csv are required "
                         "(run judge_rq5.py --annotator a and --annotator b first)")

    agreed = {k: a[k] for k in a if k in b and a[k] == b[k]}
    disagreements = [k for k in a if k in b and a[k] != b[k]]
    final = load_sheet(FINAL)
    todo = [k for k in disagreements if k not in final]

    print(f"agreed pairs: {len(agreed)}   disagreements: {len(disagreements)}   "
          f"already in final: {len(disagreements) - len(todo)}   to adjudicate: {len(todo)}")

    def build_sheet() -> dict:
        sheet = dict(agreed)
        sheet.update(final)
        return sheet

    if not todo:
        print(f"done: {FINAL} has {len(build_sheet())} pairs")
        return 0
    if args.limit:
        todo = todo[: args.limit]
    rng = random.Random(f"rq5-final-{args.seed}")
    order = rng.sample(todo, len(todo))
    resp = {(r["conv_id"], r["label"]): r
            for r in (json.loads(l) for l in RESPONSES.read_text().splitlines() if l.strip())}
    print_rubric()

    done = 0
    for i, (cid, label) in enumerate(order, 1):
        r = resp[(cid, label)]
        print(f"\n[{i}/{len(order)}] conv {cid}  label {label}  "
              f"(annotator a: {a[(cid, label)]}  annotator b: {b[(cid, label)]})")
        print("  -- turn 1 (learner) --")
        print("  " + str(r["turn1_user"]).replace("\n", "\n  "))
        print("  -- turn-1 answer --")
        print("  " + str(r["turn1_system"]).replace("\n", "\n  "))
        print("  -- turn 2 (learner) --")
        print("  " + str(r["turn2_user"]).replace("\n", "\n  "))
        print("  -- turn-2 answer --")
        print("  " + str(r["turn2_system"]).replace("\n", "\n  "))
        while True:
            v = ask("consensus?")
            if v == "q":
                print(f"saved {len(build_sheet())} pairs (resume later)")
                write_final(build_sheet())
                return 0
            if v == "?":
                print("  y = success, n = failure, s = skip this pair, r = rubric, q = save+quit")
                continue
            if v == "r":
                print_rubric()
                continue
            if v == "s":
                print("  skipped (will be asked again next run)")
                break
            final[(cid, label)] = 1 if v == "y" else 0
            sheet = build_sheet()
            write_final(sheet)
            done += 1
            break

    sheet = build_sheet()
    print(f"\nadjudicated {done}/{len(order)}; final sheet has {len(sheet)} pairs")
    if len(sheet) < len(agreed) + len(disagreements):
        print("warning: some disagreements left unresolved; re-run to finish them")
    print(f"written: {FINAL}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())