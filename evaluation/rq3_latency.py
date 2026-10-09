#!/usr/bin/env python3
"""RQ3 latency/throughput sweeps -> rq3.json (+ CSVs).

A. TTFT  — ollama llama3.2:3b /api/chat streaming, T=0, one prompt per intent
           (sampled from the primary corpus so the router resolves as intended).
           5 warmups, then >=25 timed runs per prompt.
B. e2e   — full /api/v1/orchestrate round-trips for the same intents
           (the RQ1 latency corpus already covers per-path medians; this pass
           re-confirms on the same primary corpus sample).
C. throughput — fixed 40-message mix replayed round-robin against the deployed
           orchestrator for a fixed wall-clock window at concurrency 1/2/4/8;
           report completed/min and per-request latency percentiles.

D. (optional merge) If evaluation/rq3-stage-timings.json exists — produced by
   the @QuarkusTest RQ3StageTimingEval which instruments the server-side
   Tika parse, embedding, retrieval and LLM stages inside the deployed app —
   it is merged verbatim into rq3.json["stage_timings"].

Environment is remote via AILMS_* (see infra). Run warmups per concurrency.
"""
from __future__ import annotations

import argparse
import csv
import json
import statistics
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import infra
import metrics

HERE = Path(__file__).resolve().parent
WARMUP = 5
RUNS = 25
WINDOW_S = 90.0
MIX_PER_INTENT = 8     # 8 x 5 intents = 40-message mix

INTENTS = ["CONVERSATION", "VIDEO_SEARCH", "CONTENT_ANALYSIS", "ASSESSMENT", "INSIGHT"]


def load_primary() -> dict[str, list[str]]:
    pool: dict[str, list[str]] = {i: [] for i in INTENTS}
    with open(HERE / "utterances.csv") as fh:
        for row in csv.DictReader(fh):
            if row["intent"] in pool and len(pool[row["intent"]]) < 40:
                pool[row["intent"]].append(row["utterance"])
    return pool


def _summary(xs: list[float]) -> dict:
    if not xs:
        return {"n": 0}
    base = metrics.summary(xs)
    return dict(base, p90_s=metrics.percentiles(xs, [90]).get("p90"))


def ttft_pass() -> dict:
    pool = load_primary()
    out = {}
    raw = []  # (intent, prompt, run, ttft_s, tokens, elapsed_s)
    for intent in INTENTS:
        prompt = pool[intent][0]
        for _ in range(WARMUP):
            infra.ollama_chat_stream(prompt)
        ttfts, tokens = [], []
        for run in range(RUNS):
            r = infra.ollama_chat_stream(prompt)
            ttfts.append(r["ttft_s"])
            tokens.append(r["tokens"])
            raw.append((intent, prompt, run, r["ttft_s"], r["tokens"], r["elapsed_s"]))
        out[intent] = {"prompt": prompt, **{"ttft": _summary(ttfts)},
                       "tokens_mean": statistics.mean(tokens) if tokens else 0.0}
    save_csv(HERE / "rq3-ttft.csv", ["intent", "prompt", "run", "ttft_s", "tokens", "elapsed_s"], raw)
    return out


def e2e_pass() -> dict:
    pool = load_primary()
    out = {}
    raw = []  # (intent, prompt, run, e2e_s)
    for intent in INTENTS:
        prompt = pool[intent][0]
        for _ in range(WARMUP):
            sid = f"warm-{intent.lower()}-{time.time_ns()}"
            infra.orchestrate(prompt, session_id=sid, user_id=sid)
        lat = []
        for run in range(RUNS):
            sid = f"eval-rq3-{intent.lower()}-r{run}"
            t0 = time.perf_counter()
            infra.orchestrate(prompt, session_id=sid, user_id=sid)
            d = time.perf_counter() - t0
            lat.append(d)
            raw.append((intent, prompt, run, d))
        out[intent] = {"prompt": prompt, "e2e_s": _summary(lat)}
    save_csv(HERE / "rq3-e2e.csv", ["intent", "prompt", "run", "e2e_s"], raw)
    return out


def throughput_pass(concurrency: int) -> dict:
    pool = load_primary()
    mix = [u for i in INTENTS for u in pool[i][:MIX_PER_INTENT]]
    done = []
    lock = threading.Lock()

    def worker(worker_id: int):
        idx = worker_id % len(mix)
        stop_before = None
        while stop_before is None or time.perf_counter() < stop_before:
            prompt = mix[idx % len(mix)]
            idx += 1
            t0 = time.perf_counter()
            try:
                infra.orchestrate(prompt, session_id=f"eval-rq3-{worker_id}", user_id="eval-rq3")
                with lock:
                    done.append(time.perf_counter() - t0)
            except Exception as e:
                with lock:
                    done.append(-1.0)
            if stop_before is None:
                # sync start across workers after the first call over the window
                stop_before = time.perf_counter() + WINDOW_S

    threads = [threading.Thread(target=worker, args=(i,), daemon=True) for i in range(concurrency)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    lat = [x for x in done if x >= 0.0]
    completed = len(lat)
    save_csv(HERE / f"rq3-throughput-c{concurrency}.csv",
             ["concurrency", "latency_s"], [(concurrency, x) for x in lat])
    return {
        "concurrency": concurrency,
        "window_s": WINDOW_S,
        "completed": completed,
        "per_min": completed * 60.0 / WINDOW_S,
        "latency": _summary(lat) if lat else None,
    }


def save_csv(path: Path, header: list[str], rows: list[tuple]) -> None:
    with open(path, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(header)
        w.writerows(rows)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--concurrency", default="1,2,4,8")
    ap.add_argument("--skip-ttft", action="store_true")
    ap.add_argument("--skip-e2e", action="store_true")
    ap.add_argument("--skip-throughput", action="store_true")
    args = ap.parse_args()

    results: dict = {"environment": {
        "model": infra.CHAT_MODEL, "embed_model": infra.EMBED_MODEL,
        "orchestrate": infra.ORCHESTRATE,
    }}

    if not args.skip_ttft:
        results["ttft"] = ttft_pass()
    if not args.skip_e2e:
        results["e2e"] = e2e_pass()
    if not args.skip_throughput:
        results["throughput"] = {}
        for c in [int(x) for x in args.concurrency.split(",")]:
            results["throughput"][str(c)] = throughput_pass(c)

    stage = HERE / "rq3-stage-timings.json"
    if stage.exists():
        results["stage_timings"] = json.loads(stage.read_text())

    (HERE / "rq3.json").write_text(json.dumps(results, indent=2) + "\n")
    print(json.dumps({"ttft": {k: v["ttft"]["p50"] for k, v in results.get("ttft", {}).items()},
                      "e2e": {k: v["e2e_s"]["p50"] for k, v in results.get("e2e", {}).items()},
                      "throughput": {k: v["per_min"] for k, v in results.get("throughput", {}).items()}},
                     indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())