#!/usr/bin/env python3
"""Gate validator for all evaluation datasets. Exit 0 only when every checklist
item passes; anything else prints the failures and exits non-zero.

Checks:
  1. queries.jsonl      - schema (query, target_doc, intent), intent set, >=2
                          queries per doc, target_doc present in files/, unique.
  2. qrels-a/b.jsonl    - full query coverage (every query judged by BOTH
                          annotators), relevance in {0.0,1.0}, at least one
                          relevant and one non-relevant judgment per annotator
                          (required for Cohen's kappa to be defined), the two
                          annotators disagree on >=1 item, annotator + timestamp
                          recorded per row.
  3. rq5-multiturn.jsonl - 60 conversations x exactly 2 turns, >=59 distinct
                          turn-2 texts, turn-1 != turn-2 per conversation.
  4. adversarial-links.jsonl - non-empty, prompt field present.

Usage: python3 evaluation/validate_datasets.py
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DS = HERE / "datasets"
FILES = HERE / "files"

FAILS: list[str] = []


def load(p: Path):
    if not p.exists():
        FAILS.append(f"missing dataset file: {p.name}")
        return []
    out = []
    for i, line in enumerate(p.read_text().splitlines()):
        if not line.strip():
            continue
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError as e:
            FAILS.append(f"{p.name}:{i+1} invalid JSON: {e}")
    return out


def check_queries():
    q = load(DS / "queries.jsonl")
    if not q:
        return
    allowed = {"CONTENT_ANALYSIS", "ASSESSMENT"}
    seen: set[str] = set()
    per_doc: dict[str, int] = {}
    pdfs = {p.name for p in FILES.glob("*.pdf")} if FILES.is_dir() else set()
    for r in q:
        if not {"query", "target_doc", "intent"} <= set(r):
            FAILS.append(f"queries.jsonl: row missing schema fields: {r}")
            continue
        if r["intent"] not in allowed:
            FAILS.append(f"queries.jsonl: unexpected intent {r['intent']!r}")
        if r["query"] in seen:
            FAILS.append(f"queries.jsonl: duplicate query {r['query']!r}")
        seen.add(r["query"])
        per_doc[r["target_doc"]] = per_doc.get(r["target_doc"], 0) + 1
        if pdfs and r["target_doc"] not in pdfs:
            FAILS.append(f"queries.jsonl: target_doc {r['target_doc']!r} not in files/")
    for doc, n in sorted(per_doc.items()):
        if n < 2:
            FAILS.append(f"queries.jsonl: {doc} has only {n} query (need >=2)")


def check_qrels():
    for name in ("qrels-a.jsonl", "qrels-b.jsonl"):
        rows = load(DS / name)
        if not rows:
            continue
        rels = {r["relevance"] for r in rows if "relevance" in r}
        missing = {"query", "doc", "relevance"}
        bad = [r for r in rows if not missing <= set(r)]
        if bad:
            FAILS.append(f"{name}: {len(bad)} rows missing {sorted(missing)}")
        if not rels <= {0.0, 1.0}:
            FAILS.append(f"{name}: relevance values out of allowed set: {sorted(rels)}")
        if 0.0 not in rels:
            FAILS.append(f"{name}: no non-relevant (0.0) judgments -> kappa undefined")
        if 1.0 not in rels:
            FAILS.append(f"{name}: no relevant (1.0) judgments")
        if not any("annotator" in r for r in rows):
            FAILS.append(f"{name}: annotator id not recorded per row")
        if not any("timestamp" in r for r in rows):
            FAILS.append(f"{name}: judgment timestamp not recorded per row")

    queries = {r["query"] for r in load(DS / "queries.jsonl")}
    for name in ("qrels-a.jsonl", "qrels-b.jsonl"):
        rows = load(DS / name)
        judged = {r["query"] for r in rows}
        missing_q = queries - judged
        if missing_q:
            FAILS.append(f"{name}: {len(missing_q)} queries without judgments: {sorted(missing_q)[:6]}")

    a = {(r["query"], r["doc"]) for r in load(DS / "qrels-a.jsonl")}
    b = {(r["query"], r["doc"]) for r in load(DS / "qrels-b.jsonl")}
    if a and b:
        if a == b:
            FAILS.append("qrels-a == qrels-b (identical judgments) -> kappa needs disagreement")
        elif not (a and b):
            FAILS.append("qrels empty")


def check_rq5():
    rows = load(DS / "rq5-multiturn.jsonl")
    if not rows:
        return
    by_conv: dict[str, list[int]] = {}
    for r in rows:
        if not {"conv_id", "turn", "user"} <= set(r):
            FAILS.append(f"rq5-multiturn.jsonl: row missing schema fields: {r}")
            continue
        by_conv.setdefault(r["conv_id"], []).append(r["turn"])
    if len(by_conv) != 60:
        FAILS.append(f"rq5-multiturn.jsonl: {len(by_conv)} conversations, need 60")
    for cid, turns in sorted(by_conv.items()):
        if sorted(turns) != [1, 2]:
            FAILS.append(f"rq5-multiturn.jsonl: conv {cid} turns {sorted(turns)} != [1, 2]")
    t1 = {r["user"] for r in rows if r["turn"] == 1}
    t2 = [r["user"] for r in rows if r["turn"] == 2]
    if len(set(t2)) < 59:
        FAILS.append(f"rq5-multiturn.jsonl: only {len(set(t2))} distinct turn-2 (need >=59)")
    overlap = t1 & set(t2)
    if overlap:
        FAILS.append(f"rq5-multiturn.jsonl: {len(overlap)} turn-2 texts duplicate turn-1 texts")


def check_adv_links():
    rows = load(DS / "adversarial-links.jsonl")
    if not rows:
        FAILS.append("adversarial-links.jsonl: empty (need prompt rows)")
        return
    for i, r in enumerate(rows):
        if not ("prompt" in r and isinstance(r["prompt"], str) and r["prompt"].strip()):
            FAILS.append(f"adversarial-links.jsonl: row {i} missing prompt")


def main():
    check_queries()
    check_qrels()
    check_rq5()
    check_adv_links()
    if FAILS:
        for f in FAILS:
            print(f"FAIL: {f}")
        print(f"\n{len(FAILS)} dataset validation failure(s) — gate NOT passed.")
        return 1
    print("dataset validation PASS (queries, qrels-a/b, rq5-multiturn, adversarial-links)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())