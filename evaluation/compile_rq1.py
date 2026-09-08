#!/usr/bin/env python3
"""Compile RQ1 paper-ready numbers from the live sweep outputs.

Reads evaluation/rq1-live-predictions.csv + evaluation/rq1-latencies.csv:
- identifies deterministic short-circuit rows (bare greeting -> CONVERSATION,
  YouTube URL -> VIDEO_SEARCH) using the same rules as the Java side
- router-served fraction = short-circuited / total
- latency medians: overall, short-circuit vs classifier-routed, and saved
- prints a summary ready to paste into paper.tex (tab:rq1 + methods note)

Usage: python3 evaluation/compile_rq1.py
"""
import csv
import re
import statistics
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRED_CSV = HERE / "rq1-live-predictions.csv"
LAT_CSV = HERE / "rq1-latencies.csv"

# Mirror of OrchestratorService.EXPLICIT_VIDEO_LINK
EXPLICIT_VIDEO_LINK = re.compile(
    r"(?i)\b(?:https?://|www\.)?(?:m\.)?(?:youtube\.com|youtu\.be)/"
)

# Mirror of TextUtils.BARE_GREETING (case-insensitive, whole-message match)
BARE_GREETING = re.compile(
    r"^(?:(?:hi|hiya|hello|heya|hey|yo|sup|namaste|namaskar|hola)(?:\s+there)?"
    r"|good\s+(?:morning|afternoon|evening)|how(?:'s| is| are)?\s+"
    r"(?:it\s+going|things\s+going|you\s+doing|are\s+you|are\s+things))"
    r"[\s!.,'?]*$",
    re.IGNORECASE,
)
MAX_GREETING_LENGTH = 30


def is_bare_greeting(message):
    if message is None:
        return False
    t = message.strip()
    if not t or len(t) > MAX_GREETING_LENGTH:
        return False
    return BARE_GREETING.match(t) is not None


def is_explicit_video_link(message):
    return bool(message) and bool(EXPLICIT_VIDEO_LINK.search(message))


def short_circuit(message):
    if is_bare_greeting(message):
        return "CONVERSATION"
    if is_explicit_video_link(message):
        return "VIDEO_SEARCH"
    return None


def pct(x):
    return f"{100 * x:.1f}%"


def main():
    preds = list(csv.DictReader(open(PRED_CSV, encoding="utf-8")))
    lats = {r["message"]: int(r["latency_ms"]) for r in csv.DictReader(open(LAT_CSV, encoding="utf-8"))}
    assert len(preds) == len(lats) == 218, f"preds={len(preds)} lats={len(lats)}"

    sc_rows, clf_rows = [], []
    sc_correct = 0
    for r in preds:
        sc = short_circuit(r["message"])
        lat = lats[r["message"]]
        if sc is not None:
            sc_rows.append((r, lat))
            sc_correct += sc == r["predicted"] == r["truth"]
        else:
            clf_rows.append((r, lat))

    sc_lat = sorted(lat for _, lat in sc_rows)
    clf_lat = sorted(lat for _, lat in clf_rows)
    all_lat = sorted(lats.values())
    med = statistics.median
    print(f"n_total = {len(preds)}")
    print(f"n_short_circuit = {len(sc_rows)} "
          f"({sum(1 for r, _ in sc_rows if r['truth'] == 'CONVERSATION')} greeting, "
          f"{sum(1 for r, _ in sc_rows if r['truth'] == 'VIDEO_SEARCH')} video-link)")
    print(f"router_served_fraction = {pct(len(sc_rows) / len(preds))}")
    print(f"short_circuit_correct = {sc_correct}/{len(sc_rows)}")
    print(f"n_classifier_routed = {len(clf_rows)}")
    print()
    print(f"latency_overall_ms: p50={med(all_lat):.0f} "
          f"p95={sorted(all_lat)[int(0.95 * len(all_lat))]} "
          f"p99={sorted(all_lat)[int(0.99 * len(all_lat))]}")
    print(f"latency_shortcircuit_ms: p50={med(sc_lat):.0f}")
    print(f"latency_classifier_ms: p50={med(clf_lat):.0f}")
    print(f"median_routing_latency_saved_ms = {med(clf_lat) - med(sc_lat):.0f} "
          f"(classifier p50 minus short-circuit p50)")
    print()
    print("% tab:rq1 row (support, P, R, F1) -- metrics from live-metrics.csv:")
    print("% CONVERSATION  & 50 & .526 & 1.000 & .690 \\\\")
    print("% VIDEO_SEARCH  & 48 & 1.000 & .854 & .921 \\\\")
    print("% CONTENT_ANALYSIS & 40 & .973 & .900 & .935 \\\\")
    print("% ASSESSMENT    & 40 & 1.000 & .775 & .873 \\\\")
    print("% INSIGHT       & 40 & 1.000 & .350 & .519 \\\\")
    print("% accuracy .789, macro-P .900, macro-R .776, macro-F1 .788, n=218")


if __name__ == "__main__":
    main()
