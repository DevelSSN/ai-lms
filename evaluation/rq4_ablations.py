#!/usr/bin/env python3
"""RQ4 ablation sweep -> rq4.json (+ rq4.csv).

Three ablation arms, exactly the 3 rows of Table 9 that need measurement:

  (i)  Chunk-size ablation. The deployed 800/100 row reuses the manifest data
       produced by the real ingest pipeline (ingest_corpus.py). The 400/50 and
       1600/200 rows rebuild the same corpus with a script-side chunker that
       differs ONLY in (chunk_size, overlap) — same embedder (nomic-embed-text
       v1, 768D), same dual write (per-arm Qdrant collection + pgvector scratch
       table), same retrieval operating point (3x overfetch, min-score 0.5) —
       scored as precision@k (k=8 analysis / k=3 assessment) per query against
       the rebuilt qrels.

  (ii) Zero-shot vs few-shot+negatives classifier accuracy. DEployed accuracy
       is the EXISTING pipeline run on the 218 novel utterances
       (probe-218-predictions.csv) — no rerun. The zero-shot arm sends the same
       218 utterances to llama3.2:3b T=0 with a pure instruction prompt (zero
       exemplars, no negative examples) and reports the accuracy delta.

  (iii) Deterministic-off (router disabled) precision reuse. The monolithic
        no-short-circuit run is ALREADY in rq1-monolithic-metrics.json (94.1%);
        per Phase 8.6 this cell is reused, never re-run.

Usage:
  python3 evaluation/rq4_ablations.py [--skip-chunks] [--skip-zero-shot]
  python3 evaluation/rq4_ablations.py --arm zero-shot
"""
from __future__ import annotations

import argparse
import csv
import json
import statistics
import sys
import uuid
from pathlib import Path

import infra
import metrics

HERE = Path(__file__).resolve().parent
DS = HERE / "datasets"
MANIFEST = DS / "corpus-manifest.json"
QUERIES = DS / "queries.jsonl"

CHUNK_CONFIGS = [(400, 50), (1600, 200)]
ZERO_SHOT_INTENTS = ["CONVERSATION", "VIDEO_SEARCH", "CONTENT_ANALYSIS", "ASSESSMENT", "INSIGHT"]
ZERO_SHOT_SYSTEM = (
    "Classify the learner's intent. Return ONLY one of: "
    "CONVERSATION, VIDEO_SEARCH, CONTENT_ANALYSIS, ASSESSMENT, INSIGHT. "
    "CONTENT_ANALYSIS asks about uploaded material; ASSESSMENT requests quiz/questions "
    "on uploaded material; INSIGHT asks for statistics/references across sessions; "
    "conversational chatter is CONVERSATION. No further text."
)


def mean_std(xs):
    if not xs:
        return {"mean": None, "sd": None, "n": 0}
    n = len(xs)
    return {"mean": statistics.fmean(xs), "sd": statistics.stdev(xs) if n > 1 else 0.0, "n": n}


def extract_corpus_text() -> dict[str, str]:
    manifest = json.loads(MANIFEST.read_text())
    out = {}
    for doc_id in manifest:
        r = infra.psql(f"SELECT extractedtext FROM content_documents WHERE id = '{doc_id}';")
        if r.returncode == 0 and r.stdout:
            out[doc_id] = r.stdout
    if not out:
        raise SystemExit("no extracted corpus text; run ingest_corpus.py first")
    return out


def chunk_window(text: str, size: int, overlap: int) -> list[str]:
    """Boundary-free contiguous window chunks at a fixed (size, overlap)."""
    step = max(size - overlap, 1)
    return [text[i : i + size] for i in range(0, len(text), step)]


def build_arm(size: int, overlap: int) -> str:
    collection = f"ailms-eval-chunk-{size}-{overlap}"
    table = f"chunk_ablation_{size}_{overlap}".replace("-", "_")
    print(f"build arm {size}/{overlap} -> qdrant {collection}, pg {table}")
    infra.qdrant_recreate_collection(collection)
    infra.psql(f"DROP TABLE IF EXISTS {table};")
    docs = extract_corpus_text()
    for idx, (doc_id, text) in enumerate(docs.items()):
        source = f"doc:{doc_id}"
        chunks = chunk_window(text, size, overlap)
        if idx == 0:
            print(f"  {doc_id}: {len(chunks)} chunks @ {size}/{overlap}")
        qpoints = []
        pg_rows = []
        for chunk in chunks:
            vec = infra.ollama_embed(chunk)
            cid = str(uuid.uuid4())
            qpoints.append({"id": cid, "vector": vec, "payload": {"source": source, "type": "document"}})
            pg_rows.append((cid, source, doc_id, vec))
        for start in range(0, len(qpoints), 200):
            infra.qdrant_upsert(collection, qpoints[start:start + 200])
        infra.pg_upsert_vectors(table, pg_rows)
    return collection, table


def load_queries() -> list[dict]:
    return [json.loads(l) for l in QUERIES.read_text().splitlines() if l.strip()]


def load_relevant() -> dict[str, set[str]]:
    qrels_b = []
    for ann in ("a", "b"):
        p = DS / f"qrels-{ann}.jsonl"
        if not p.exists():
            continue
        qrels_b += [json.loads(l) for l in p.read_text().splitlines() if l.strip()]
    manifest = json.loads(MANIFEST.read_text())
    inv = {meta["filename"]: doc_id for doc_id, meta in manifest.items()}
    out: dict[str, set[str]] = {}
    for r in qrels_b:
        if r.get("relevance", 0.0) >= 1.0 and r["doc"] in inv:
            out.setdefault(r["query"], set()).add(inv[r["doc"]])
    return out


def chunk_arm(size: int, overlap: int, qrels_rel: dict[str, set[str]]) -> dict:
    collection, table = build_arm(size, overlap)
    queries = load_queries()
    p = []
    for q in queries:
        k = 3 if q["intent"] == "ASSESSMENT" else 8
        vec = infra.ollama_embed(q["query"])
        rel = qrels_rel.get(q["query"], set())
        for store, call in (("qdrant", lambda: infra.qdrant_ranked(vec, k, collection)),
                            ("pgvector", lambda: infra.pg_ranked(vec, k, table))):
            ranked = call()
            ids = [(r["doc_id"] or ((r.get("source") or "")[4:] if (r.get("source") or "").startswith("doc:") else "")) for r in ranked]
            p.append({"store": store, "P@k": metrics.precision_at_k(ids[:k], rel), "k": k})
    per_store = {}
    for store in ("qdrant", "pgvector"):
        vals = [x["P@k"] for x in p if x["store"] == store]
        per_store[store] = {"precision_at_k": mean_std(vals)}
    return {"chunk": size, "overlap": overlap, "collection": collection, **per_store}


def zero_shot_accuracy() -> dict:
    held = HERE / "probe-novel" / "heldout-218.csv"
    preds = HERE / "probe-218-predictions.csv"
    rows = list(csv.DictReader(open(held)))
    fs = {}
    if preds.exists():
        fs_rows = {r["message"]: r["predicted"] for r in csv.DictReader(open(preds))}
        fs = {"correct": sum(1 for r in rows if fs_rows.get(r["message"]) == r["truth"]),
              "n": len(rows)}

    zs_correct = 0
    for row in rows:
        out = infra.ollama_chat(row["message"], system=ZERO_SHOT_SYSTEM)
        predicted = out.strip().split()[0].upper() if out else ""
        predicted = predicted.strip(".,:;\"'")
        norm = next((i for i in ZERO_SHOT_INTENTS
                     if predicted.startswith(i) or i.startswith(predicted)), None)
        if norm == row["truth"]:
            zs_correct += 1

    zs = {"correct": zs_correct, "n": len(rows), "accuracy": zs_correct / len(rows)}
    if fs:
        fs["accuracy"] = fs["correct"] / fs["n"]
        zs["delta_vs_fewshot"] = zs["accuracy"] - fs["accuracy"]
    return {"zero_shot": zs, "few_shot_negatives_reused": fs}


def deterministic_off_reuse() -> dict:
    p = HERE / "rq1-monolithic-metrics.json"
    if not p.exists():
        raise SystemExit("rq1-monolithic-metrics.json missing (deterministic-off arm)")
    data = json.loads(p.read_text())
    return {"accuracy": data["accuracy"]}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--arm", choices=["chunks", "zero-shot", "all"], default="all")
    ap.add_argument("--skip-chunks", action="store_true")
    ap.add_argument("--skip-zero-shot", action="store_true")
    args = ap.parse_args()

    if args.arm == "zero-shot":
        return emit(json.dumps({"zero_shot": zero_shot_accuracy()}, indent=2) + "\n")

    out: dict = {"environment": {"embed_model": infra.EMBED_MODEL, "min_score": infra.MIN_SCORE,
                                 "overfetch": infra.OVERFETCH}}
    if not args.skip_chunks:
        qrels_rel = load_relevant()
        if not qrels_rel:
            raise SystemExit("no qrels available — complete annotate_qrels.py first")
        out["chunk_ablations"] = [chunk_arm(size, overlap, qrels_rel) for size, overlap in CHUNK_CONFIGS]
    if not args.skip_zero_shot:
        out["classifier_ablation"] = zero_shot_accuracy()
    out["deterministic_off"] = deterministic_off_reuse()

    emit(json.dumps(out, indent=2) + "\n")
    return 0


def emit(text: str) -> int:
    (HERE / "rq4.json").write_text(text)
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())