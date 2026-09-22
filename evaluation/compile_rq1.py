#!/usr/bin/env python3
"""Compile RQ1 intent-routing metrics from the committed sweep CSVs for tab:rq1.

Hybrid mode (default): reads `rq1-live-predictions.csv` (message,truth,predicted)
and `rq1-latencies.csv` (message,truth,predicted,latency_ms). Mirrors the
production routing prefix so the 'router-served fraction' and latency-saved
numbers are consistent with the deployed short-circuits:

  - bare greeting  -> CONVERSATION (no LLM call)
  - explicit video link -> VIDEO_SEARCH (no LLM call)
  - explicit video request -> VIDEO_SEARCH (no LLM call)
  - else -> LLM classifier

Monolithic mode (--monolithic): reads `rq1-monolithic-predictions.csv` and
`rq1-monolithic-latencies.csv` produced by baseline_sweep.py (bypassRoutes=true,
pure classifier on every utterance, no short-circuits, no re-classification).
Every row therefore went through the LLM. Reports the full-matrix metrics plus a
`router_servable_subset`: how the monolithic classifier fares on the rows the
hybrid router would have served with no model call at all, and at what latency.

One-vs-rest P/R/F1 math is identical to RoutingMetrics.score (TP/FP/FN
definitions and zero-division -> 0.0), so the per-intent numbers match the
livePredictions test.

Usage:
  python3 evaluation/compile_rq1.py
  python3 evaluation/compile_rq1.py --monolithic
"""
import csv
import json
import re
import statistics
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

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

# Mirrors TextUtils.EXPLICIT_VIDEO_REQUEST (request frame + video noun).
EXPLICIT_VIDEO_REQUEST = re.compile(
    r"(?i)(?:\b(?:search\s+for|look\s+for|find|get\s+me|recommend|suggest\s+(?:a|some)|"
    r"link\s+(?:a|\w+\s+)?|show(?:\s+me)?|i\s+(?:need|want(?:\s+to\s+watch)?)|"
    r"do\s+you\s+have|can\s+you\s+find|is\s+there|have\s+you\s+got|any)\b"
    r"(?:.*?)\b(?:video|videos|clip|clips|tutorial|tutorials|youtube|"
    r"visual\s+guide|educational\s+videos?)\b"
    r"|\b(?:tutorial\s+videos?|video\s+examples?)\s+(?:about|on|for)\b)"
)

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


def is_explicit_video_request(msg: str) -> bool:
    return bool(msg) and bool(EXPLICIT_VIDEO_REQUEST.search(msg))


def is_router_servable(msg: str) -> bool:
    return (
        is_bare_greeting(msg)
        or is_explicit_video_link(msg)
        or is_explicit_video_request(msg)
    )


def load_csv(path: Path) -> list[dict]:
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def p50(values: list[float]) -> float:
    return statistics.median(values)


def p95(values: list[float]) -> float:
    return sorted(values)[int(0.95 * len(values))]


def p99(values: list[float]) -> float:
    return sorted(values)[int(0.99 * len(values))]


def scores_for(rows: list[tuple[str, str, int]]) -> dict:
    confusion = {t: {p: 0 for p in LABELS} for t in LABELS}
    correct = 0
    for pred, truth, _ in rows:
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

    return {
        "n": len(rows),
        "confusion": confusion,
        "per_label": per_label,
        "accuracy": correct / len(rows),
        "macro_precision": sum_p / len(LABELS),
        "macro_recall": sum_r / len(LABELS),
        "macro_f1": sum_f / len(LABELS),
        "correct": correct,
    }


def dedupe_map(raw_rows: list[dict]) -> dict:
    return {r["message"]: r for r in raw_rows}


def main() -> None:
    monolithic = "--monolithic" in sys.argv
    probe = "--probe" in sys.argv

    if probe:
        PREDS = HERE / "probe-218-predictions.csv"
        LATS = HERE / "probe-218-latencies.csv"
        out_json = HERE / "probe-218-metrics.json"
        corpus_path = HERE / "probe-novel" / "heldout-218.csv"
    elif monolithic:
        PREDS = HERE / "rq1-monolithic-predictions.csv"
        LATS = HERE / "rq1-monolithic-latencies.csv"
        out_json = HERE / "rq1-monolithic-metrics.json"
        corpus_path = HERE / "utterances.csv"
    else:
        PREDS = HERE / "rq1-live-predictions.csv"
        LATS = HERE / "rq1-latencies.csv"
        out_json = HERE / "rq1-metrics.json"
        corpus_path = HERE / "utterances.csv"

    with open(corpus_path, newline="", encoding="utf-8") as f:
        corpus_rows = list(csv.DictReader(f))
    n_expected = len({r["utterance"] for r in corpus_rows})

    preds = list(dedupe_map(load_csv(PREDS)).values())
    lats = list(dedupe_map(load_csv(LATS)).values())

    if len(preds) != n_expected or len(lats) != n_expected:
        raise SystemExit(
            f"expected {n_expected} unique rows, found preds={len(preds)} lats={len(lats)}"
        )

    lat_by_msg = {r["message"]: int(r["latency_ms"]) for r in lats}
    for p in preds:
        if p["message"] not in lat_by_msg:
            raise SystemExit(f"no latency for message: {p['message']}")

    rows = [(normalize_intent(p["predicted"]), normalize_intent(p["truth"]), lat_by_msg[p["message"]]) for p in preds]
    all_lat = sorted(lat_by_msg.values())

    if not monolithic:
        sc_rows, clf_rows = [], []
        for p in preds:
            pred = normalize_intent(p["predicted"])
            truth = normalize_intent(p["truth"])
            lat = lat_by_msg[p["message"]]
            if is_router_servable(p["message"]):
                sc_rows.append((pred, truth, lat, p["message"]))
            else:
                clf_rows.append((pred, truth, lat, p["message"]))

        sc_correct = sum(1 for pred, truth, _, _ in sc_rows if pred == truth)
        sc_greetings = sum(1 for _, _, _, msg in sc_rows if is_bare_greeting(msg))
        sc_links = sum(1 for _, _, _, msg in sc_rows if is_explicit_video_link(msg))
        sc_video = sum(
            1
            for _, _, _, msg in sc_rows
            if is_explicit_video_link(msg) or is_explicit_video_request(msg)
        )
        # A false short-circuit is a router-served row whose fixed intent is wrong
        # for its label (e.g. a greeting regex firing on a non-greeting row).
        false_sc = [
            (pred, truth, msg)
            for pred, truth, _, msg in sc_rows
            if (is_bare_greeting(msg) and truth != "CONVERSATION")
            or (
                (is_explicit_video_link(msg) or is_explicit_video_request(msg))
                and truth != "VIDEO_SEARCH"
            )
        ]

        scores = scores_for(rows)
        median_all = p50(all_lat)
        median_clf = p50(sorted(lat for _, _, lat, _ in clf_rows))
        median_sc = p50(sorted(lat for _, _, lat, _ in sc_rows))

        report = {
            "n": scores["n"],
            "per_label": scores["per_label"],
            "accuracy": scores["accuracy"],
            "macro_precision": scores["macro_precision"],
            "macro_recall": scores["macro_recall"],
            "macro_f1": scores["macro_f1"],
            "short_circuit_count": len(sc_rows),
            "short_circuit_greeting": sc_greetings,
            "short_circuit_video_link": sc_links,
            "short_circuit_video_request": sc_video - sc_links,
            "short_circuit_correct": sc_correct,
            "false_short_circuits": len(false_sc),
            "router_served_frac": len(sc_rows) / scores["n"] * 100.0,
            "classifier_observed_count": len(clf_rows),
            "classifier_observed_correct": sum(
                1 for pred, truth, _, _ in clf_rows if pred == truth
            ),
            "median_all_latency_ms": median_all,
            "median_classifier_latency_ms": median_clf,
            "median_short_latency_ms": median_sc,
            "median_latency_saved_ms": median_clf - median_sc,
            "p95_all_latency_ms": p95(all_lat),
            "p99_all_latency_ms": p99(all_lat),
            "confusion": scores["confusion"],
        }
        out_json.write_text(json.dumps(report, indent=2), encoding="utf-8")

        print(f"{'INTENT':<16}{'SHARE%':>8}{'SUPPORT':>9}{'PREC':>9}{'RECALL':>9}{'F1':>9}")
        for label in LABELS:
            support, precision, recall, f1 = scores["per_label"][label]
            print(
                f"{label:<16}{support / scores['n'] * 100:>8.1f}{support:>9d}"
                f"{precision:>9.3f}{recall:>9.3f}{f1:>9.3f}"
            )
        print(f"\nOverall accuracy:            {scores['accuracy']:.3f}")
        print(f"Macro P/R/F1:               {scores['macro_precision']:.3f} / {scores['macro_recall']:.3f} / {scores['macro_f1']:.3f}")
        print(f"Short-circuited rows:       {len(sc_rows)} / {scores['n']} "
              f"({sc_greetings} greeting, {sc_links} link, {sc_video - sc_links} request)")
        print(f"Short-circuit correct:      {sc_correct}/{len(sc_rows)}")
        print(f"False short-circuits:       {len(false_sc)}")
        print(f"Classifier-observed:        {report['classifier_observed_correct']}/{len(clf_rows)} "
              f"({report['classifier_observed_correct'] / len(clf_rows):.3f})")
        print(f"Router-served fraction (%): {len(sc_rows) / scores['n'] * 100.0:.1f}")
        print(f"Latency overall ms:         p50={median_all:.0f} p95={p95(all_lat)} p99={p99(all_lat)}")
        print(f"Latency short-circuit ms:   p50={median_sc:.0f}")
        print(f"Latency classifier ms:      p50={median_clf:.0f}")
        print(f"Median routing latency saved:{median_clf - median_sc:.0f} ms")
    else:
        scores = scores_for(rows)
        median_all = p50(all_lat)

        serveable_rows = [
            (pred, truth, lat) for p, (pred, truth, lat)
            in zip(preds, rows) if is_router_servable(p["message"])
        ]
        serveable_correct = sum(1 for pred, truth, _ in serveable_rows if pred == truth)
        serveable_lat = sorted(lat for _, _, lat in serveable_rows)

        report = {
            "n": scores["n"],
            "per_label": scores["per_label"],
            "accuracy": scores["accuracy"],
            "macro_precision": scores["macro_precision"],
            "macro_recall": scores["macro_recall"],
            "macro_f1": scores["macro_f1"],
            "confusion": scores["confusion"],
            "median_all_latency_ms": median_all,
            "p95_all_latency_ms": p95(all_lat),
            "p99_all_latency_ms": p99(all_lat),
            "router_servable_subset": {
                "count": len(serveable_rows),
                "correct": serveable_correct,
                "accuracy": serveable_correct / len(serveable_rows) if serveable_rows else None,
                "median_latency_ms": p50(serveable_lat) if serveable_rows else None,
            },
        }
        out_json.write_text(json.dumps(report, indent=2), encoding="utf-8")

        print(f"{'INTENT':<16}{'SHARE%':>8}{'SUPPORT':>9}{'PREC':>9}{'RECALL':>9}{'F1':>9}")
        for label in LABELS:
            support, precision, recall, f1 = scores["per_label"][label]
            print(
                f"{label:<16}{support / scores['n'] * 100:>8.1f}{support:>9d}"
                f"{precision:>9.3f}{recall:>9.3f}{f1:>9.3f}"
            )
        print(f"\nMonolithic accuracy:        {scores['accuracy']:.3f}")
        print(f"Macro P/R/F1:               {scores['macro_precision']:.3f} / {scores['macro_recall']:.3f} / {scores['macro_f1']:.3f}")
        ss = report["router_servable_subset"]
        print(f"Router-servable subset:     {ss['correct']}/{ss['count']} "
              f"({ss['accuracy']:.3f}), median latency {ss['median_latency_ms']:.0f} ms")
        print(f"Latency overall ms:         p50={median_all:.0f} p95={p95(all_lat)} p99={p99(all_lat)}")

    print(f"\nWrote {out_json}")


if __name__ == "__main__":
    main()