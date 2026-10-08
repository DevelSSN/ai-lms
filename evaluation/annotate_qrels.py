#!/usr/bin/env python3
"""Interactive RQ2 qrels annotation workbook (Phase 7.1).

Each annotator independently judges every (query, document) pair pooled by
build_qrels_pool.py, labeling relevance 0/1 INCLUDING negatives, with their
annotator id and a timestamp on every row. Running state persists
incrementally, so an annotator can stop and resume. The validator
(validate_datasets.py) enforces: full query coverage, both labels present,
annotator+timestamp, and qrels-a != qrels-b — so no annotator can copy the
other's file.

Conveniences:
  --prefill-gold   marks each query's own target_doc as relevant (1) up front
                   (the query was authored against that document), leaving only
                   the pooled distractors to be judged interactively.
  --snippets       show a text snippet of the candidate document before judging.

Usage:
  python3 evaluation/annotate_qrels.py --annotator a --prefill-gold
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
DS = HERE / "datasets"
POOL = DS / "qrels-pool.jsonl"
TIMESTAMP_FMT = "%Y-%m-%dT%H:%M:%SZ"


def existing_rows(annotator: str) -> list[dict]:
    p = DS / f"qrels-{annotator}.jsonl"
    if not p.exists():
        return []
    return [json.loads(l) for l in p.read_text().splitlines() if l.strip()]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--annotator", required=True, choices=["a", "b"])
    ap.add_argument("--prefill-gold", action="store_true")
    ap.add_argument("--snippets", action="store_true")
    args = ap.parse_args()

    if not POOL.exists():
        raise SystemExit(f"{POOL} missing — run build_qrels_pool.py first")

    pool = [json.loads(l) for l in POOL.read_text().splitlines() if l.strip()]
    pairs = {(r["query"], r["doc"]) for r in pool}
    queries = {q["query"]: q["target_doc"] for q in
               [json.loads(l) for l in (DS / "queries.jsonl").read_text().splitlines() if l.strip()]}

    out_path = DS / f"qrels-{args.annotator}.jsonl"
    done = existing_rows(args.annotator)
    done_keys = {(r["query"], r["doc"]) for r in done}
    if args.prefill_gold:
        for r in pool:
            if queries.get(r["query"]) == r["doc"] and (r["query"], r["doc"]) not in done_keys:
                done.append({
                    "query": r["query"], "doc": r["doc"], "relevance": 1.0,
                    "annotator": f"annotator-{args.annotator}",
                    "timestamp": datetime.now(timezone.utc).strftime(TIMESTAMP_FMT),
                    "prefilled_gold": True,
                })
                done_keys.add((r["query"], r["doc"]))
        write_rows(out_path, done)

    todo = sorted(pairs - done_keys, key=lambda k: (k[0], k[1]))
    print(f"annotator {args.annotator}: {len(done)} judged, {len(todo)} remaining")
    if not todo:
        print("nothing left to judge")
        return 0

    for query, doc in todo:
        prompt = queries.get(query, "?")
        gold_hint = "  [GOLD target]" if queries.get(query) == doc else ""
        while True:
            print(f"\n=== Q: {query}")
            print(f"    candidate doc: {doc}{gold_hint}")
            if args.snippets:
                try:
                    import infra
                    import json as _json
                    manifest = _json.loads((DS / "corpus-manifest.json").read_text())
                    doc_id = next((d for d, m in manifest.items() if m["filename"] == doc), None)
                    if doc_id:
                        r = infra.psql(
                            f"SELECT left({infra.pg_column('content_documents', 'extractedText')}, 200) "
                            f"FROM content_documents WHERE id = '{doc_id}';"
                        )
                        if r.returncode == 0 and r.stdout.strip():
                            print(f"    snippet: {r.stdout.strip()[:200]!r}")
                except Exception as e:
                    print(f"    (snippet unavailable: {e})")
            ans = input("   relevance 0 / 1 / s(kip) / q(uit): ").strip().lower()
            if ans in ("0", "1"):
                done.append({
                    "query": query, "doc": doc, "relevance": float(ans),
                    "annotator": f"annotator-{args.annotator}",
                    "timestamp": datetime.now(timezone.utc).strftime(TIMESTAMP_FMT),
                })
                done_keys.add((query, doc))
                write_rows(out_path, done)
                break
            if ans == "s":
                break
            if ans == "q":
                break
            print("  enter 0, 1, s, or q")
        if ans == "q":
            break
    print(f"saved {len(existing_rows(args.annotator))} judgments -> {out_path}")
    print("next: python3 evaluation/validate_datasets.py")
    return 0


def write_rows(path: Path, rows: list[dict]) -> None:
    path.write_text("".join(json.dumps(minimalize(r)) + "\n" for r in rows))


def minimalize(r: dict) -> dict:
    out = {"query": r["query"], "doc": r["doc"], "relevance": r["relevance"],
           "annotator": r["annotator"], "timestamp": r["timestamp"]}
    if r.get("prefilled_gold"):
        out["prefilled_gold"] = True
    return out


if __name__ == "__main__":
    raise SystemExit(main())