#!/usr/bin/env python3
"""Monolithic full-LLM baseline sweep: POST every corpus utterance to the
orchestrator with bypassRoutes=true, forcing the pure classifier path
(no deterministic short-circuits, no document re-classification), and record
the classifier's predicted intent (agentType) + per-call latency.

Resume-capable. Progressively flushes CSVs so a poll shows live progress.

Isolation mirrors rq1_sweep.py: every row runs as a FRESH user (zero history)
so rows cannot contaminate each other. Doc-intent rows (CONTENT_ANALYSIS,
ASSESSMENT) get a per-user Science.pdf seed; all other rows run doc-less.

Usage:
  python3 evaluation/baseline_sweep.py [--fresh]
  tail -f evaluation/baseline_sweep.log
"""
import csv
import json
import subprocess
import sys
import time
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
CORPUS = HERE / "utterances.csv"
PRED_CSV = HERE / "rq1-monolithic-predictions.csv"
LAT_CSV = HERE / "rq1-monolithic-latencies.csv"
ENDPOINT = "http://localhost:10082/api/v1/orchestrate"
DOC_INTENTS = {"CONTENT_ANALYSIS", "ASSESSMENT"}
SEED_KEY = "uploads/rq1-seeded/science.pdf"
TIMEOUT_S = 600
RETRIES = 3


def seed_doc_for_user(user):
    """Insert a ContentDocument row pointing at the shared MinIO seed object."""
    sql = (
        "INSERT INTO content_documents (id, userid, sessionid, filename, filetype,"
        f" filesize, storagepath, status, uploadedat) VALUES ('rq1-mono-doc-{user}',"
        f" '{user}', 'rq1-mono-seed', 'Science.pdf', 'application/pdf',"
        f" (SELECT filesize FROM content_documents WHERE id='rq1-science-doc'),"
        f" '{SEED_KEY}', 'UPLOADED', now()) ON CONFLICT (id) DO NOTHING;"
    )
    r = subprocess.run(
        ["podman", "exec", "postgres", "psql", "-U", "ailms", "-d", "ailms", "-c", sql],
        capture_output=True,
        text=True,
    )
    if r.returncode != 0:
        raise RuntimeError(f"doc seed failed for user={user}: {r.stderr.strip()}")


def post(message, user):
    body = json.dumps(
        {"message": message, "sessionId": str(uuid.uuid4()), "bypassRoutes": True}
    ).encode()
    last_err = None
    for attempt in range(RETRIES):
        req = urllib.request.Request(
            ENDPOINT,
            data=body,
            headers={"Content-Type": "application/json", "X-User-Id": user},
        )
        t0 = time.monotonic()
        try:
            with urllib.request.urlopen(req, timeout=TIMEOUT_S) as resp:
                payload = json.loads(resp.read().decode())
            dt_ms = int((time.monotonic() - t0) * 1000)
            return payload.get("agentType", ""), dt_ms
        except Exception as e:  # noqa: BLE001 - retry then abort loudly
            last_err = e
            time.sleep(5 * (attempt + 1))
    raise RuntimeError(f"POST failed after {RETRIES} tries: {last_err}")


def main():
    subprocess.run([sys.executable, str(HERE / "seed_science.py")], check=True)

    fresh = "--fresh" in sys.argv
    if fresh:
        for p in (PRED_CSV, LAT_CSV):
            try:
                p.unlink()
            except FileNotFoundError:
                pass

    with open(CORPUS, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    done = {}
    try:
        with open(PRED_CSV, newline="", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                done[r["message"]] = r["predicted"]
    except FileNotFoundError:
        pass

    with open(PRED_CSV, "a", newline="", encoding="utf-8") as pf, open(
        LAT_CSV, "a", newline="", encoding="utf-8"
    ) as lf:
        pw = csv.writer(pf)
        lw = csv.writer(lf)
        if not done:
            pw.writerow(["message", "truth", "predicted"])
            lw.writerow(["message", "truth", "predicted", "latency_ms"])
        for i, r in enumerate(rows, 1):
            if r["utterance"] in done:
                continue
            user = f"rq1-mono-u-{uuid.uuid4().hex[:8]}"
            if r["intent"] in DOC_INTENTS:
                seed_doc_for_user(user)
            intent, dt = post(r["utterance"], user)
            pw.writerow([r["utterance"], r["intent"], intent])
            lw.writerow([r["utterance"], r["intent"], intent, dt])
            pf.flush()
            lf.flush()
            print(
                f"[{i}/{len(rows)}] user={user} truth={r['intent']} pred={intent} {dt}ms :: {r['utterance'][:60]}",
                flush=True,
            )
    print("MONOLITHIC SWEEP COMPLETE", flush=True)


if __name__ == "__main__":
    sys.exit(main())