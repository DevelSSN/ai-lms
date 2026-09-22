#!/usr/bin/env python3
"""Convert the hand-authored held-out test corpus to the sweep schema.

Source: orchestrator/src/test/resources/eval/utterances.csv (header `intent,message`,
CRLF, quoted multi-line fields). Output: probe-novel/heldout-218.csv with
`utterance,intent` columns, matching the RQ1 sweep's DictReader contract.

Usage: python3 evaluation/make_heldout_csv.py
"""
import csv
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = (
    HERE / ".." / "orchestrator" / "src" / "test" / "resources" / "eval"
    / "utterances.csv"
)
OUT_DIR = HERE / "probe-novel"
OUT = OUT_DIR / "heldout-218.csv"


def main():
    OUT_DIR.mkdir(exist_ok=True)
    with open(SRC, newline="", encoding="utf-8") as f:
        rows = [(intent.strip(), msg.strip()) for intent, msg in csv.reader(f)][1:]
    rows = [r for r in rows if r[0] and r[1]]
    with open(OUT, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["utterance", "intent"])
        w.writerows((msg, intent) for intent, msg in rows)
    print(f"wrote {len(rows)} rows -> {OUT}")


if __name__ == "__main__":
    main()