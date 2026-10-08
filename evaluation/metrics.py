#!/usr/bin/env python3
"""Shared evaluation metrics for RQ1-RQ5. Stdlib only.

Implements the exact metric definitions the paper promises in its reporting
rules: dispersion (SD or IQR) alongside every aggregate, Cohen's kappa for
inter-annotator agreement, retrieval metrics at both operating points, Wald
confidence intervals, and McNemar's test for the paired RQ1 comparison.

All functions are deterministic and return JSON-serialisable values.
"""
from __future__ import annotations

import math
import statistics
from typing import Iterable, Sequence


# ---------------------------------------------------------------------------
# Retrieval metrics (document-level / chunk-level via relevant-id sets)
# ---------------------------------------------------------------------------

def precision_at_k(retrieved: Sequence[str], relevant: set[str], k: int | None = None) -> float:
    """Fraction of the top-k retrieved ids that are relevant (0.0 if k=0)."""
    top = retrieved[:k] if k is not None else retrieved
    if not top:
        return 0.0
    return sum(1 for rid in top if rid in relevant) / len(top)


def recall_at_k(retrieved: Sequence[str], relevant: set[str], k: int | None = None) -> float:
    """Fraction of all relevant ids present in the top-k (0.0 if no relevant ids)."""
    if not relevant:
        return 0.0
    top = retrieved[:k] if k is not None else retrieved
    return sum(1 for rid in top if rid in relevant) / len(relevant)


def reciprocal_rank(retrieved: Sequence[str], relevant: set[str]) -> float:
    """1/rank of the first relevant id, 0 if none retrieved."""
    for i, rid in enumerate(retrieved, start=1):
        if rid in relevant:
            return 1.0 / i
    return 0.0


def ndcg_at_k(retrieved: Sequence[str], relevant: set[str], k: int | None = None) -> float:
    """nDCG@k with binary gain (rel=1 for relevant). Ideal = all relevant at top."""
    top = retrieved[:k] if k is not None else retrieved
    if not top:
        return 0.0
    dcg = 0.0
    for i, rid in enumerate(top, start=1):
        if rid in relevant:
            dcg += 1.0 / math.log2(i + 1)
    idcg = 0.0
    for i in range(1, min(len(relevant), len(top)) + 1):
        idcg += 1.0 / math.log2(i + 1)
    return dcg / idcg if idcg > 0 else 0.0


def list_agreement(a: Sequence[str], b: Sequence[str], k: int) -> float:
    """|A_topk intersect B_topk| / k — Qdrant vs pgvector dual-write agreement."""
    if k <= 0:
        return 0.0
    return len(set(a[:k]) & set(b[:k])) / k


# ---------------------------------------------------------------------------
# Inter-annotator agreement
# ---------------------------------------------------------------------------

def cohens_kappa(a: Sequence[int], b: Sequence[int]) -> float | None:
    """Cohen's kappa for two hard-label annotations (len must match).

    Returns None (NOT a fabricated number) when agreement is artefactually
    degenerate — e.g. both annotators use a single label, giving p_e = 1 and a
    zero denominator. Callers must treat None as 'unreported', never as 0.
    """
    if len(a) != len(b) or not a:
        raise ValueError("kappa requires two equal-length, non-empty rating lists")
    n = len(a)
    n_agreed = sum(1 for x, y in zip(a, b) if x == y)
    p_o = n_agreed / n
    labels = sorted(set(a) | set(b))
    p_e = sum(
        (sum(1 for x in a if x == lab) / n) * (sum(1 for y in b if y == lab) / n)
        for lab in labels
    )
    if p_e >= 1.0:  # single-label annotations -> denominator zero
        return None
    return (p_o - p_e) / (1.0 - p_e)


# ---------------------------------------------------------------------------
# Dispersion + summarisation (reporting rules: every aggregate has dispersion)
# ---------------------------------------------------------------------------

def percentiles(values: Sequence[float], ps: Iterable[float] = (50, 95, 99)) -> dict[str, float]:
    """Linear-interpolated percentiles keyed by 'p50','p95', etc."""
    if not values:
        return {}
    ordered = sorted(values)
    out: dict[str, float] = {}
    for p in ps:
        if p == 50 and len(ordered) % 2 == 0:
            out[f"p{int(p)}"] = statistics.median(ordered)
            continue
        idx = (len(ordered) - 1) * (p / 100.0)
        lo = math.floor(idx)
        hi = math.ceil(idx)
        if lo == hi:
            out[f"p{int(p)}"] = float(ordered[lo])
        else:
            frac = idx - lo
            out[f"p{int(p)}"] = float(ordered[lo] + frac * (ordered[hi] - ordered[lo]))
    return out


def summary(values: Sequence[float]) -> dict:
    """mean, sd, iqr, min, max, and p50/p95/p99 — the dispersion contract."""
    if not values:
        return {}
    ordered = sorted(values)
    n = len(ordered)
    sd = statistics.stdev(ordered) if n > 1 else 0.0
    q1 = ordered[n // 4] if n >= 4 else ordered[0]
    q3 = ordered[(3 * n) // 4] if n >= 4 else ordered[-1]
    out: dict = {
        "n": n,
        "mean": statistics.fmean(ordered),
        "sd": sd,
        "iqr": q3 - q1,
        "min": ordered[0],
        "max": ordered[-1],
    }
    out.update(percentiles(ordered))
    return out


def wald_ci(successes: int, n: int, z: float = 1.96) -> dict[str, float]:
    """95% (default) Wald interval for a proportion; guards n=0."""
    if n <= 0:
        return {"point": None, "lo": None, "hi": None}
    p = successes / n
    se = math.sqrt(p * (1 - p) / n)
    lo = max(0.0, p - z * se)
    hi = min(1.0, p + z * se)
    return {"point": p, "lo": lo, "hi": hi}


def mcnemar(b: int, c: int) -> dict:
    """McNemar's test on discordant pairs (hybrid wrong/mono right = b, and vice
    versa = c), with continuity correction; also the exact binomial two-sided
    p-value when b + c <= 25 (large-sample chi-square is unreliable there)."""
    pairs = b + c
    if pairs == 0:
        return {"pairs": 0, "chi2_cc": None, "p_value": 1.0}
    disc = abs(b - c)
    chi2_cc = (disc - 1) ** 2 / pairs if disc > 0 else 0.0
    p_chi = math.exp(-chi2_cc / 2) if chi2_cc > 0 else 1.0  # df=1 survival
    p_exact = None
    if pairs <= 25:
        p_exact = 2 * sum(
            math.comb(pairs, k) * (0.5**pairs) for k in range(0, min(b, c) + 1)
        )
        p_exact = min(1.0, p_exact)
    return {
        "pairs": pairs,
        "b": b,
        "c": c,
        "chi2_cc": chi2_cc,
        "p_large_sample": p_chi,
        "p_exact_binomial": p_exact,
    }


def one_vs_rest(truth: Sequence[str], pred: Sequence[str]) -> dict:
    """Per-label precision/recall/F1 plus macro averages and overall accuracy."""
    labels = sorted(set(truth))
    per: dict = {}
    for lab in labels:
        tp = sum(1 for t, p in zip(truth, pred) if t == lab and p == lab)
        fp = sum(1 for t, p in zip(truth, pred) if t != lab and p == lab)
        fn = sum(1 for t, p in zip(truth, pred) if t == lab and p != lab)
        prec = tp / (tp + fp) if tp + fp else 0.0
        rec = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
        per[lab] = {
            "n": sum(1 for t in truth if t == lab),
            "precision": prec,
            "recall": rec,
            "f1": f1,
        }
    n = len(truth)
    acc = sum(1 for t, p in zip(truth, pred) if t == p) / n if n else None
    macro = {m: statistics.fmean(p[m] for p in per.values()) for m in ("precision", "recall", "f1")}
    return {"per_label": per, "accuracy": acc, "macro": macro, "n": n}


def aggregate_over_queries(per_query: Sequence[dict]) -> dict:
    """Collapse per-query metric dicts into mean/SD across queries + 95% CI."""
    keys = {k for d in per_query for k in d if k not in {"query"}}
    out: dict = {}
    for k in sorted(keys):
        vals = [float(d[k]) for d in per_query if k in d]
        if not vals:
            continue
        s = summary(vals)
        ci = wald_ci(sum(vals), len(vals))  # mean proportion CI (valid for 0/1 metrics)
        out[k] = {"mean": s["mean"], "sd": s["sd"], "n": s["n"], "ci95": [ci["lo"], ci["hi"]]}
    return out