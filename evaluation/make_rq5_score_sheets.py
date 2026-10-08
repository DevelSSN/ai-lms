#!/usr/bin/env python3
"""Generate blind RQ5 score sheets for the two annotators.

Reads the config-blind responses file written by rq5_memory.py and emits
rq5-scores-a.csv / rq5-scores-b.csv exactly in the shape aggregate.table10
consumes: header  conv_id,label,success  with one row per (conv_id,label),
`label` the rotated x/y/z (never a/b/c), `success` blank for the annotator to
fill 1/0 per datasets/rq5-rubric.md. Row order is shuffled with a per-annotator
seed so the two annotators never judge in the same order.

Usage:
  python3 evaluation/make_rq5_score_sheets.py
  # -> evaluation/rq5-scores-a.csv, evaluation/rq5-scores-b.csv
"""
from __future__ import annotations

import csv
import json
import random
from pathlib import Path

HERE = Path(__file__).resolve().parent
RESPONSES = HERE / "rq5-responses.jsonl"
ANNOTATORS = ("a", "b")
COLUMNS = ["conv_id", "label", "success"]


def load_responses() -> list[dict]:
    if not RESPONSES.exists():
        raise SystemExit(f"{RESPONSES} missing - run rq5_memory.py first")
    return [json.loads(l) for l in RESPONSES.read_text().splitlines() if l.strip()]


def main() -> int:
    rows = load_responses()
    pairs = sorted({(r["conv_id"], r["label"]) for r in rows})
    if not pairs:
        raise SystemExit("no (conv_id,label) pairs in rq5-responses.jsonl")
    for ann in ANNOTATORS:
        rng = random.Random(f"rq5-{ann}-20241001")
        order = rng.sample(pairs, len(pairs))
        out = HERE / f"rq5-scores-{ann}.csv"
        with open(out, "w", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(COLUMNS)
            w.writerows((cid, lab, "") for cid, lab in order)
        print(f"wrote {out}: {len(order)} (conv_id,label) rows to judge "
              f"(blind labels; per datasets/rq5-rubric.md)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())