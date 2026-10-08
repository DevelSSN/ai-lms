#!/usr/bin/env python3
"""RQ2 retrieval-quality evaluation -> rq2.json (+ rq2-query-results.csv).

For each query in datasets/queries.jsonl:
  - embed once (nomic-embed-text)
  - rank from Qdrant (collection ailms-content) and from pgvector, each at the
    deployed operating point (k=8 analysis, k=3 assessment; 3x overfetch, then
    drop candidates below ailms.rag.min-score, then take k)
  - score P@k, R@k, nDCG@k, and MRR (over the min-score-filtered ranking)
    against the document-level gold relevance judgments
  - record |Qdrant_topk intersect pgvector_topk| / k (dual-write agreement)

Then re-scores against the flat-chunk, no-overlap baseline arm: the same corpus
chunked by the script into contiguous 800-char slices (NO boundary snap, NO
overlap), embedded the same way into a separate Qdrant collection, scored by P@k
at both operating points.

Inter-annotator Cohen's kappa is computed from the qrels-a/qrels-b judgments;
a None (degenerate) value is reported as null, never fabricated.

Usage:
  python3 evaluation/rq2_retrieval.py [--flat-only] [--skip-flat]
"""
from __future__ import annotations

import argparse
import json
import uuid
from pathlib import Path

import infra
import metrics

HERE = Path(__file__).resolve().parent
DS = HERE / "datasets"
MANIFEST = DS / "corpus-manifest.json"
QUERIES = DS / "queries.jsonl"
QRELS_A = DS / "qrels-a.jsonl"
QRELS_B = DS / "qrels-b.jsonl"
FLAT_COLLECTION = "ailms-eval-flat"
FLAT_CHUNK_SIZE = 800

ANALYSIS_K = 8
ASSESSMENT_K = 3


def load_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        raise SystemExit(f"missing dataset: {path}")
    return [json.loads(l) for l in path.read_text().splitlines() if l.strip()]


def doc_ids_for_query(relevant_filenames: set[str], inv_manifest: dict[str, str]) -> set[str]:
    out = set()
    for fn in relevant_filenames:
        ids = [doc for doc, name in inv_manifest.items() if name == fn]
        out.update(ids)
    return out


def score_retrieval(ranked: list[dict], relevant: set[str], k: int) -> dict:
    retrieved_ids = [r["doc_id"] or _doc_from_source(r["source"]) for r in ranked]
    top_k = retrieved_ids[:k]
    relevant_doc_ids = {rid for rid in retrieved_ids if rid in relevant}
    return {
        "P@k": metrics.precision_at_k(top_k, relevant),
        "R@k": metrics.recall_at_k(top_k, relevant),
        "nDCG@k": metrics.ndcg_at_k(top_k, relevant),
        "MRR": metrics.reciprocal_rank(retrieved_ids, relevant),
        "k": k,
        "retrieved_relevant": len(relevant_doc_ids),
    }


def _doc_from_source(source: str | None) -> str:
    if source and source.startswith("doc:"):
        return source[4:]
    return source or ""


def run_store(store_name: str, query: dict, k: int, doc_to_relevant: dict[str, set[str]]):
    """Per-query, single-store scoring (legacy helper retained for --flat arms)."""
    vec = infra.ollama_embed(query["query"])
    if store_name == "qdrant":
        ranked = infra.qdrant_ranked(vec, k)
    elif store_name == "pgvector":
        ranked = infra.pg_ranked(vec, k)
    elif store_name == "flat":
        ranked = infra.qdrant_ranked(vec, k, FLAT_COLLECTION)
    else:
        raise ValueError(store_name)
    relevant = doc_to_relevant.get(query["query"], set())
    scores = score_retrieval(ranked, relevant, k)
    return {"store": store_name, "k": k, "scores": scores}


def extract_corpus_text() -> dict[str, str]:
    manifest = json.loads(MANIFEST.read_text())
    out = {}
    for doc_id in manifest:
        r = infra.psql(
            f"SELECT {infra.pg_column('content_documents', 'extractedText')} "
            f"FROM content_documents WHERE id = '{doc_id}';"
        )
        if r.returncode != 0:
            continue
        text = r.stdout
        if text:
            out[doc_id] = text
    return out


def flat_chunk(text: str, size: int = FLAT_CHUNK_SIZE) -> list[str]:
    """Contiguous slices, NO boundary snap, NO overlap — the flat baseline arm."""
    return [text[i : i + size] for i in range(0, len(text), size)]


def build_flat_collection() -> None:
    print(f"building flat-chunk baseline collection {FLAT_COLLECTION}")
    infra.qdrant_recreate_collection(FLAT_COLLECTION)
    docs = extract_corpus_text()
    if not docs:
        raise SystemExit("no extracted corpus text found; run ingest_corpus.py first")
    for doc_id, text in docs.items():
        source = f"doc:{doc_id}"
        chunks = flat_chunk(text)
        points = []
        for i, chunk in enumerate(chunks):
            vec = infra.ollama_embed(chunk)
            points.append(
                {
                    "id": str(uuid.uuid4()),
                    "vector": vec,
                    "payload": {"source": source, "type": "document", "chunkIndex": i},
                }
            )
        for start in range(0, len(points), 200):
            infra.qdrant_upsert(FLAT_COLLECTION, points[start : start + 200])
        print(f"  {doc_id}: {len(chunks)} flat chunks")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--skip-flat", action="store_true")
    ap.add_argument("--flat-only", action="store_true")
    args = ap.parse_args()

    if not MANIFEST.exists():
        raise SystemExit(f"{MANIFEST} missing — run ingest_corpus.py first")
    manifest = json.loads(MANIFEST.read_text())
    inv = {doc: meta["filename"] for doc, meta in manifest.items()}
    queries = load_jsonl(QUERIES)
    qrels_a = load_jsonl(QRELS_A)
    qrels_b = load_jsonl(QRELS_B)

    # document-level relevant filenames per query (union of both annotators)
    doc_rel: dict[str, set[str]] = {}
    for row in qrels_a + qrels_b:
        if row.get("relevance", 0.0) >= 1.0:
            doc_rel.setdefault(row["query"], set()).add(row["doc"])
    relevant_ids = {
        q: doc_ids_for_query(fns, inv) for q, fns in doc_rel.items()
    }

    if args.flat_only:
        build_flat_collection()
        return 0

    kappa = metrics.cohens_kappa(
        [int(r.get("relevance", 0) >= 1.0) for r in qrels_a],
        [int(r.get("relevance", 0) >= 1.0) for r in qrels_b],
    )

    results = {"queries_evaluated": len(queries), "cohens_kappa_a_vs_b": kappa, "per_query": []}
    per_q_rows = []
    for q in queries:
        query = q["query"]; k = ASSESSMENT_K if q["intent"] == "ASSESSMENT" else ANALYSIS_K
        vec = infra.ollama_embed(query)
        ranked_q = infra.qdrant_ranked(vec, k)
        ranked_p = infra.pg_ranked(vec, k)
        relevant = relevant_ids.get(query, set())
        q_scores = score_retrieval(ranked_q, relevant, k)
        p_scores = score_retrieval(ranked_p, relevant, k)
        agree = metrics.list_agreement(
            [r["doc_id"] or _doc_from_source(r["source"]) for r in ranked_q[:k]],
            [r["doc_id"] or _doc_from_source(r["source"]) for r in ranked_p[:k]],
            k,
        )
        entry = {
            "query": query, "intent": q["intent"], "k": k,
            "qdrant": q_scores, "pgvector": p_scores, "agreement": agree,
            "target_doc": q["target_doc"],
        }
        results["per_query"].append(entry)
        per_q_rows.append((query, k, q_scores, p_scores, agree))

    # aggregate per operating point and per store
    def ctx(op: str, k: int):
        rows = [r for r in results["per_query"] if r["k"] == k]
        q_list = [r["qdrant"] for r in rows]
        p_list = [r["pgvector"] for r in rows]
        return {
            "qdrant": metrics.aggregate_over_queries(
                [{"P@k": s["P@k"], "R@k": s["R@k"], "nDCG@k": s["nDCG@k"], "MRR": s["MRR"]} for s in q_list]
            ),
            "pgvector": metrics.aggregate_over_queries(
                [{"P@k": s["P@k"], "R@k": s["R@k"], "nDCG@k": s["nDCG@k"], "MRR": s["MRR"]} for s in p_list]
            ),
            "agreement_mean": metrics.summary([r["agreement"] for r in rows if r["agreement"] is not None]),
            "n_queries": len(rows),
        }

    results["k8"] = ctx("k8", ANALYSIS_K)
    results["k3"] = ctx("k3", ASSESSMENT_K)

    if not args.skip_flat:
        build_flat_collection()
        flat = {"k8": {"P@k": []}, "k3": {"P@k": []}}
        for q in queries:
            k = ASSESSMENT_K if q["intent"] == "ASSESSMENT" else ANALYSIS_K
            vec = infra.ollama_embed(q["query"])
            ranked = infra.qdrant_ranked(vec, k, FLAT_COLLECTION)
            relevant = relevant_ids.get(q["query"], set())
            scores = score_retrieval(ranked, relevant, k)
            flat[f"k{k}"]["P@k"].append(scores["P@k"])
        results["flat_baseline"] = {
            "k8": metrics.aggregate_over_queries([{"P@k": v} for v in flat["k8"]["P@k"]]),
            "k3": metrics.aggregate_over_queries([{"P@k": v} for v in flat["k3"]["P@k"]]),
        }

    out_path = HERE / "rq2.json"
    out_path.write_text(json.dumps(results, indent=2) + "\n")
    with open(HERE / "rq2-query-results.csv", "w") as fh:
        fh.write("query,k,qdrant_P@k,qdrant_R@k,qdrant_nDCG@k,qdrant_MRR,pg_P@k,pg_R@k,pg_nDCG@k,pg_MRR,agreement\n")
        for (query, k, qs, ps, ag) in per_q_rows:
            agree_cell = "" if ag is None else f"{ag:.4f}"
            fh.write(f"{query},{k},{qs['P@k']:.4f},{qs['R@k']:.4f},{qs['nDCG@k']:.4f},{qs['MRR']:.4f},"
                     f"{ps['P@k']:.4f},{ps['R@k']:.4f},{ps['nDCG@k']:.4f},{ps['MRR']:.4f},{agree_cell}\n")
    print(f"wrote {out_path} (n={len(queries)}, kappa={kappa})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())