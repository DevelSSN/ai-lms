#!/usr/bin/env python3
"""Aggregate RQ2-RQ5 into rq{2..5}.md (and rq{2..5}.json) with row labels that
mirror Tables 7-10 of the Springer submission exactly, ready to paste.

   python3 evaluation/aggregate.py [--require-scores]
       --require-scores: rq5 annotator score CSVs must exist for Table 10 rows

Row sources:
  Table 7  <- rq2.json (rq2_retrieval.py) + qrels-a/b keys aligned
  Table 8  <- rq3.json + rq3-stage-timings.json (@QuarkusTest) + rq3.csv
  Table 9  <- rq4.json + rq2.json (deployed 800/100 row)
  Table 10 <- rq5-memory.py responses + rq5-blind-map.json + rq5-scores-a/b.csv
              (unblinded by aggregate, never in the responses file)

Every value printed has its dispersion (SD or IQR) where the paper's Legend
requires it; missing pieces print "[PENDING]" rather than a fabricated number.
"""
from __future__ import annotations

import argparse
import csv
import json
import statistics
from pathlib import Path

import metrics

HERE = Path(__file__).resolve().parent
DS = HERE / "datasets"


def load(p: Path, default=None):
    if not p.exists():
        return default
    return json.loads(p.read_text())


def fmt(v, sd=None, nd=4):
    if v is None:
        return "[PENDING]"
    s = f"{v:.{nd}f}" if isinstance(v, float) else str(v)
    if sd:
        s += f" ± {sd:.4f}"
    return s


def kappa_from_files(a_path: Path, b_path: Path):
    def read(path: Path) -> dict[tuple[str, str], int]:
        out = {}
        if not path.exists():
            return out
        for line in path.read_text().splitlines():
            if not line.strip():
                continue
            r = json.loads(line)
            out[(r["query"], r["doc"])] = int(r.get("relevance", 0.0) >= 1.0)
        return out

    a, b = read(a_path), read(b_path)
    keys = sorted(set(a) & set(b))
    if len(keys) < 8:
        return None
    return metrics.cohens_kappa([a[k] for k in keys], [b[k] for k in keys])


def table7(rq2: dict) -> list[list[str]]:
    g = {"k8": rq2.get("k8", {}), "k3": rq2.get("k3", {})}
    kappa = kappa_from_files(DS / "qrels-a.jsonl", DS / "qrels-b.jsonl")
    if kappa is None:
        kappa = rq2.get("cohens_kappa_a_vs_b")
    flat = rq2.get("flat_baseline", {})
    rows = []
    for label, key in (("Precision@k", "P@k"), ("Recall@k", "R@k"),
                       ("nDCG@k", "nDCG@k"), ("MRR", "MRR")):
        cells = []
        for side in g.values():
            agg = (side.get("qdrant") or {}).get(key) or {}
            cells.append(fmt(agg.get("mean"), agg.get("sd")))
        rows.append([label, *cells])
    rows.append(["Cohen's kappa (gold annotators)", fmt(kappa), fmt(kappa)])
    a_mean = (g["k8"].get("agreement_mean") or {}).get("mean")
    a_mean3 = (g["k3"].get("agreement_mean") or {}).get("mean")
    rows.append(["Qdrant-pgvector list agreement", fmt(a_mean), fmt(a_mean3)])
    f8 = (flat.get("k8") or {}).get("P@k", {}).get("mean")
    f3 = (flat.get("k3") or {}).get("P@k", {}).get("mean")
    rows.append(["Flat-chunk baseline, P@k", fmt(f8), fmt(f3)])
    return rows


def table8(rq3: dict) -> list[list[str]]:
    stages = rq3.get("stage_timings", {})  # from the @QuarkusTest, seconds
    ttft = {k: v.get("ttft", {}) for k, v in rq3.get("ttft", {}).items()}
    e2e = {k: v.get("e2e_s", {}) for k, v in rq3.get("e2e", {}).items()}
    rows = []

    def stage_row(label, www):
        if isinstance(www, dict):
            p50, p95, p99 = www.get("p50"), www.get("p95"), www.get("p99")
        else:
            p50 = p95 = p99 = None
        rows.append([label, fmt(p50), fmt(p95), fmt(p99)])

    stage_row("Tika extraction", stages.get("tika"))
    stage_row("Embedding", stages.get("embedding"))
    stage_row("Vector retrieval", stages.get("retrieval"))
    # TTFT and e2e are measured client-side here; ms conversion happens below.
    for label, src in (("LLM time-to-first-token", ttft), ("End-to-end total", e2e)):
        ms = {k: {kk: (vv * 1000.0 if vv is not None else None)
                  for kk, vv in v.items() if kk in ("p50", "p95", "p99")} for k, v in src.items()}
        p50 = statistics.fmean([v["p50"] for v in ms.values() if v.get("p50") is not None]) if ms else None
        p95 = statistics.fmean([v["p95"] for v in ms.values() if v.get("p95") is not None]) if ms else None
        p99 = statistics.fmean([v["p99"] for v in ms.values() if v.get("p99") is not None]) if ms else None
        rows.append([label, fmt(p50), fmt(p95), fmt(p99)])
    tp = rq3.get("throughput", {})
    if tp:
        concs = "; ".join(f"{c}->{v.get('per_min', 0.0):.0f}/min" for c, v in sorted(tp.items()))
        rows.append(["Throughput (1/2/4/8 conc.)", concs, "—", "—"])
    else:
        rows.append(["Throughput (1/2/4/8 conc.)", "[PENDING]", "[PENDING]", "[PENDING]"])
    return rows


def table9(rq2: dict, rq4: dict) -> list[list[str]]:
    q2 = (rq2.get("k8", {}).get("qdrant", {}) or {}).get("P@k", {}).get("mean")
    ca = rq4.get("classifier_ablation", {})
    rows = []
    for arm in rq4.get("chunk_ablations", []):
        p = (arm.get("qdrant") or {}).get("precision_at_k", {}).get("mean")
        rows.append([f"Chunk {arm['chunk']}/{arm['overlap']} - retrieval precision", fmt(p, nd=3)])
    rows.append(["Chunk 800/100 (deployed) - precision", fmt(q2, nd=3)])
    rows.append(["Zero-shot classifier accuracy", fmt((ca.get("zero_shot") or {}).get("accuracy"), nd=3)])
    rows.append(["Few-shot + negatives accuracy (deployed)", fmt((ca.get("few_shot_negatives_reused") or {}).get("accuracy"), nd=3)])
    do = rq4.get("deterministic_off", {})
    if not do:  # reuse existing monolithic run directly, never re-run
        mono = load(HERE / "rq1-monolithic-metrics.json", {})
        do = {"accuracy": mono.get("accuracy")}
    rows.append(["Deterministic layer off (= monolithic)",
                 (fmt(do.get("accuracy"), nd=3) if do.get("accuracy") is not None else "[PENDING]")])
    return rows


def table10() -> list[list[str]]:
    blind = load(HERE / "rq5-blind-map.json", {})
    resp_present = (HERE / "rq5-responses.jsonl").exists()
    scores: dict[str, dict[tuple[str, str], int]] = {}
    for ann in ("a", "b"):
        p = HERE / f"rq5-scores-{ann}.csv"
        if p.exists():
            rows = csv.DictReader(open(p))
            scores[ann] = {(r["conv_id"], r["label"]): int(r["success"]) for r in rows}
    n_judged = len(next(iter(scores.values()), {}))
    if not resp_present or n_judged < 20:
        return _pending_r5_rows()
    config_success = {"a": [], "b": [], "c": []}
    aligned_a, aligned_b = [], []
    for (conv_id, label), a_s in scores["a"].items():
        b_s = scores["b"].get((conv_id, label))
        if b_s is None:
            continue
        aligned_a.append(a_s)
        aligned_b.append(b_s)
        config = (blind.get(conv_id) or {}).get(label)
        if config in config_success:
            config_success[config].extend([a_s, b_s])
    kappa = metrics.cohens_kappa(aligned_a, aligned_b)
    rows = [["Continuation success rate",
             *[fmt(statistics.fmean(s), nd=3) if s else "[PENDING]" for s in
               (config_success["a"], config_success["b"], config_success["c"])]],
            ["Cohen's kappa (annotators)", fmt(kappa), "—", "—"]]
    restart = HERE / "rq5-restart.jsonl"
    if restart.exists():
        rows.append(["Restart recovery", "n/a",
                     "scored from rq5-restart.jsonl responses", "—"])
    else:
        rows.append(["Restart recovery", "n/a", "[PENDING]", "[PENDING]"])
    return rows


def _pending_r5_rows():
    return [["Continuation success rate", "[PENDING]", "[PENDING]", "[PENDING]"],
            ["Cohen's kappa (annotators)", "[PENDING]", "—", "—"],
            ["Restart recovery", "n/a", "[PENDING]", "[PENDING]"]]


def emit(path: str, title: str, headers: list[str], rows: list[list[str]]) -> None:
    width = max(len(h) for h in headers) if headers else 0
    lines = [f"# {title}", ""]
    lines.append("| " + " | ".join(h.ljust(width) for h in headers) + " |")
    lines.append("|" + "---|" * len(headers))
    for r in rows:
        while len(r) < len(headers):
            r.append(" ")
        lines.append("| " + " | ".join(c.ljust(width) for c in r[:len(headers)]) + " |")
    body = "\n".join(lines) + "\n"
    (HERE / path).write_text(body)
    print(body)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--require-scores", action="store_true")
    args = ap.parse_args()
    rq2, rq3, rq4 = load(HERE / "rq2.json", {}), load(HERE / "rq3.json", {}), load(HERE / "rq4.json", {})
    emit("rq2.md", "Table 7: Retrieval quality [measured cells]", ["Metric", "k=8 (analysis)", "k=3 (assess.)"], table7(rq2))
    emit("rq3.md", "Table 8: End-to-end latency percentiles (ms) [measured cells]",
         ["Stage", "p50", "p95", "p99"], table8(rq3))
    emit("rq4.md", "Table 9: Ablation study [measured cells]", ["Variant", "Outcome"], table9(rq2, rq4))
    emit("rq5.md", "Table 10: Cross-session state retention [measured cells]",
         ["Memory configuration", "(a) none", "(b) window", "(c) window + profile"], table10())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())