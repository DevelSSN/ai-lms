#!/usr/bin/env python3
"""Build the RQ2 qrels pooling set (Phase 7.1).

For each query in datasets/queries.jsonl, pool the top-10 documents from the
Qdrant *and* top-10 from the pgvector deployment collections (17 queries x up
to 10 each -> 22..28 queries x up to 20 pooled (query, document) pairs). The
result is datasets/qrels-pool.jsonl — the exact set the two annotators then
judge 0/1 independently via annotate_qrels.py.

Matched document identity: the deployment writes source=doc:<docId> in both
stores; docId -> filename comes from datasets/corpus-manifest.json (written by
ingest_corpus.py). Every pooled row records its rank in each store and where it
was found, so the annotation workbook can show a snippet on demand.

Usage:
  python3 evaluation/build_qrels_pool.py            # all 28 queries
  python3 evaluation/build_qrels_pool.py --query "..."  # single
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import infra

HERE = Path(__file__).resolve().parent
DS = HERE / "datasets"
MANIFEST = DS / "corpus-manifest.json"
QUERIES = DS / "queries.jsonl"
POOL = DS / "qrels-pool.jsonl"
TOP = 10


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--query", help="pool a single query only")
    args = ap.parse_args()

    if not MANIFEST.exists():
        raise SystemExit(f"{MANIFEST} missing — run ingest_corpus.py first")
    manifest = json.loads(MANIFEST.read_text())
    doc_file = {doc_id: meta["filename"] for doc_id, meta in manifest.items()}

    queries = [json.loads(l) for l in QUERIES.read_text().splitlines() if l.strip()]
    if args.query:
        queries = [q for q in queries if q["query"] == args.query]
        if not queries:
            raise SystemExit(f"query {args.query!r} not found")

    rows: list[dict] = []
    touched: set[tuple[str, str]] = set()
    for q in queries:
        vec = infra.ollama_embed(q["query"])
        qd = infra.qdrant_ranked(vec, TOP)
        pg = infra.pg_ranked(vec, TOP)
        for store_name, ranked in (("qdrant", qd), ("pgvector", pg)):
            for rank, hit in enumerate(ranked, start=1):
                src = hit["source"] or ""
                doc_id = hit["doc_id"] or (src[4:] if src.startswith("doc:") else "")
                filename = doc_file.get(doc_id)
                if not filename:
                    print(f"  warn: {q['query']!r}: {store_name} hit {src!r} not in manifest")
                    continue
                key = (q["query"], filename)
                if key in touched:
                    continue
                touched.add(key)
                rows.append({
                    "query": q["query"], "intent": q["intent"], "doc": filename,
                    "pooled_from": store_name, "rank": rank,
                })

    POOL.write_text("".join(json.dumps(r) + "\n" for r in rows))
    print(f"pooled {len(rows)} (query,doc) pairs across {len(queries)} queries -> {POOL}")
    print("next: python3 evaluation/annotate_qrels.py --annotator a   (and --annotator b)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())