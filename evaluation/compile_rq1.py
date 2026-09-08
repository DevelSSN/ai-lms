#!/usr/bin/env python3
"""Compile RQ1 intent-routing metrics from the committed sweep CSVs for tab:rq1.

Reads `rq1-live-predictions.csv` (message,truth,predicted) and `rq1-latencies.csv`
(message,truth,predicted,latency_ms). Mirrors the production routing prefix so the
'router-served fraction' and latency-saved numbers are consistent with the deployed
short-circuits:

  - bare greeting  -> CONVERSATION (no LLM call)
  - explicit video link -> VIDEO_SEARCH (no LLM call)
  - else -> LLM classifier

One-vs-rest P/R/F1 math is identical to RoutingMetrics.score (TP/FP/FN definitions and
zero-division -> 0.0), so the per-intent numbers match the livePredictions test.

Usage: python3 evaluation/compile_rq1.py
"""
import csv
import json
import re
import statistics
from pathlib import Path

HERE = Path(__file__).resolve().parent
PREDS = HERE / "rq1-live-predictions.csv"
LATS = HERE / "rq1-latencies.csv"
N_EXPECTED = 218

LABELS = ["CONVERSATION", "VIDEO_SEARCH", "CONTENT_ANALYSIS", "ASSESSMENT", "INSIGHT"]

# Mirrors OrchestratorService.EXPLICIT_VIDEO_LINK / TestRouter.EXPLICIT_VIDEO_LINK.
EXPLICIT_VIDEO_LINK = re.compile(
    r"(?i)\b(?:https?://|www\.)?(?:m\.)?(?:youtube\.com|youtu\.be)/"
)

# Mirrors TextUtils.BARE_GREETING (case-insensitive, whole-message match, length <=30).
BARE_GREETING = re.compile(
    r"^(?:(?:hi|hiya|hello|heya|hey|yo|sup|namaste|namaskar|hola)(?:\s+there)?"
    r"|good\s+(?:morning|afternoon|evening)|how(?:'s| is| are)?\s+"
    r"(?:it\s+going|things\s+going|you\s+doing|are\s+you|are\s+things))"
    r"[\s!.,'?]*$",
    re.IGNORECASE,
)
MAX_GREETING_LENGTH = 30

INTENT_NORMALIZER = {label: label for label in LABELS}


def normalize_intent(raw: str) -> str:
    if not raw:
        return "CONVERSATION"
    norm = raw.strip().upper().rstrip(".")
    return INTENT_NORMALIZER.get(norm, "CONVERSATION")


def is_bare_greeting(msg: str) -> bool:
    if not msg:
        return False
    trimmed = msg.strip()
    if not trimmed or len(trimmed) > MAX_GREETING_LENGTH:
        return False
    return bool(BARE_GREETING.match(trimmed))


def is_explicit_video_link(msg: str) -> bool:
    return bool(msg) and bool(EXPLICIT_VIDEO_LINK.search(msg))


def load_csv(path: Path) -> list[dict]:
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def p50(values: list[float]) -> float:
    return statistics.median(values)


def p95(values: list[float]) -> float:
    return sorted(values)[int(0.95 * len(values))]


def p99(values: list[float]) -> float:
    return sorted(values)[int(0.99 * len(values))]


def main() -> None:
    preds = load_csv(PREDS)
    lats = load_csv(LATS)
    n = len(preds)
    if n != N_EXPECTED or len(lats) != N_EXPECTED:
        raise SystemExit(f"expected {N_EXPECTED} rows, found preds={n} lats={len(lats)}")

    lat_by_msg = {r["message"]: int(r["latency_ms"]) for r in lats}
    for p in preds:
        if p["message"] not in lat_by_msg:
            raise SystemExit(f"no latency for message: {p['message']}")

    sc_rows, clf_rows = [], []
    for p in preds:
        truth = normalize_intent(p["truth"])
        pred = normalize_intent(p["predicted"])
        lat = lat_by_msg[p["message"]]
        if is_bare_greeting(p["message"]) or is_explicit_video_link(p["message"]):
            sc_rows.append((pred, truth, lat, p["message"]))
        else:
            clf_rows.append((pred, truth, lat, p["message"]))

    # Short-circuit / router-served accounting.
    sc_correct = sum(1 for pred, truth, _, _ in sc_rows if pred == truth)
    sc_greetings = sum(1 for _, truth, _, msg in sc_rows if is_bare_greeting(msg))
    sc_video = sum(1 for _, truth, _, msg in sc_rows if is_explicit_video_link(msg))
    router_served_frac = len(sc_rows) / n * 100.0

    # Confusion matrix + one-vs-rest P/R/F1 (identical to RoutingMetrics.score).
    confusion = {t: {p: 0 for p in LABELS} for t in LABELS}
    correct = 0
    for p in preds:
        truth = normalize_intent(p["truth"])
        pred = normalize_intent(p["predicted"])
        confusion[truth][pred] += 1
        if truth == pred:
            correct += 1

    per_label = {}
    sum_p = sum_r = sum_f = 0.0
    for label in LABELS:
        tp = confusion[label][label]
        fp = sum(confusion[t][label] for t in LABELS if t != label)
        fn = sum(confusion[label][p] for p in LABELS if p != label)
        support = tp + fn
        precision = tp / (tp + fp) if (tp + fp) else 0.0
        recall = tp / (tp + fn) if (tp + fn) else 0.0
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0
        per_label[label] = (support, precision, recall, f1)
        sum_p += precision
        sum_r += recall
        sum_f += f1

    accuracy = correct / n
    macro_p = sum_p / len(LABELS)
    macro_r = sum_r / len(LABELS)
    macro_f = sum_f / len(LABELS)

    # Latency medians.
    all_lat = sorted(lat_by_msg.values())
    sc_lat = sorted(lat for _, _, lat, _ in sc_rows)
    clf_lat = sorted(lat for _, _, lat, _ in clf_rows)
    median_all = p50(all_lat)
    median_clf = p50(clf_lat)
    median_sc = p50(sc_lat)
    median_saved = median_clf - median_sc

    report = {
        "n": n,
        "per_label": per_label,
        "accuracy": accuracy,
        "macro_precision": macro_p,
        "macro_recall": macro_r,
        "macro_f1": macro_f,
        "short_circuit_count": len(sc_rows),
        "short_circuit_greeting": sc_greetings,
        "short_circuit_video_link": sc_video,
        "short_circuit_correct": sc_correct,
        "router_served_frac": router_served_frac,
        "median_all_latency_ms": median_all,
        "median_classifier_latency_ms": median_clf,
        "median_short_latency_ms": median_sc,
        "median_latency_saved_ms": median_saved,
        "p95_all_latency_ms": p95(all_lat),
        "p99_all_latency_ms": p99(all_lat),
        "confusion": confusion,
    }
    (HERE / "rq1-metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

    # Human-readable summary (matches tab:rq1 skeleton).
    print(f"{'INTENT':<16}{'SHARE%':>8}{'SUPPORT':>9}{'PREC':>9}{'RECALL':>9}{'F1':>9}")
    for label in LABELS:
        support, precision, recall, f1 = per_label[label]
        print(
            f"{label:<16}{support / n * 100:>8.1f}{support:>9d}"
            f"{precision:>9.3f}{recall:>9.3f}{f1:>9.3f}"
        )
    print(f"\nOverall accuracy:            {accuracy:.3f}")
    print(f"Macro P/R/F1:               {macro_p:.3f} / {macro_r:.3f} / {macro_f:.3f}")
    print(f"Short-circuited rows:       {len(sc_rows)} / {n} "
          f"({sc_greetings} greeting, {sc_video} video-link)")
    print(f"Short-circuit correct:      {sc_correct}/{len(sc_rows)}")
    print(f"Router-served fraction (%): {router_served_frac:.1f}")
    print(f"Latency overall ms:         p50={median_all:.0f} p95={p95(all_lat)} p99={p99(all_lat)}")
    print(f"Latency short-circuit ms:   p50={median_sc:.0f}")
    print(f"Latency classifier ms:      p50={median_clf:.0f}")
    print(f"Median routing latency saved:{median_saved:.0f} ms")
    print(f"\nWrote {HERE / 'rq1-metrics.json'}")


if __name__ == "__main__":
    main()