#!/usr/bin/env python3
"""RQ1 live routing sweep: POST each corpus utterance to the orchestrator,
record predicted intent (agentType) + per-call latency. Resume-capable.
Progressively flushes CSVs so a poll shows live progress.

Isolation: every row runs as a FRESH user (zero history) so rows cannot
contaminate each other (late INSIGHT rows 500 when user history bloats the
analytics context). Doc-intent rows (CONTENT_ANALYSIS, ASSESSMENT) get a
per-user Science.pdf seed; all other rows run doc-less.

Usage:
  python3 evaluation/rq1_sweep.py [--fresh]
  python3 evaluation/rq1_sweep.py --corpus probe-novel/heldout-218.csv --out-prefix probe-218 --fresh
  tail -f evaluation/rq1_sweep.log
"""
import argparse
import csv
import json
import subprocess
import sys
import time
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
ENDPOINT = "http://localhost:10082/api/v1/orchestrate"
# Rows whose truth needs an uploaded file get a per-user Science.pdf seed;
# all other rows run doc-less, so doc-gating only affects doc-dependent intents.
DOC_INTENTS = {"CONTENT_ANALYSIS", "ASSESSMENT"}
SEED_KEY = "uploads/rq1-seeded/science.pdf"
TIMEOUT_S = 600
RETRIES = 3


def seed_doc_for_user(user):
    """Insert a ContentDocument row pointing at the shared MinIO seed object."""
    sql = (
        "INSERT INTO content_documents (id, userid, sessionid, filename, filetype,"
        f" filesize, storagepath, status, uploadedat) VALUES ('rq1-doc-{user}',"
        f" '{user}', 'rq1-seed', 'Science.pdf', 'application/pdf',"
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
        {"message": message, "sessionId": str(uuid.uuid4())}
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
    parser = argparse.ArgumentParser(description="RQ1 live routing sweep")
    parser.add_argument("--fresh", action="store_true", help="restart from scratch")
    parser.add_argument(
        "--corpus",
        default=str(HERE / "utterances.csv"),
        help="input corpus CSV with utterance,intent headers (default utterances.csv)",
    )
    parser.add_argument(
        "--out-prefix",
        default="rq1-live",
        help="output prefix -> <prefix>-predictions.csv and <prefix>-latencies.csv",
    )
    args = parser.parse_args()

    corpus = Path(args.corpus)
    if not corpus.exists() and not corpus.is_absolute():
        alt = HERE / args.corpus
        if alt.exists():
            corpus = alt
        else:
            sys.exit(f"corpus not found: {args.corpus} (cwd) nor {alt}")
    pred_csv = HERE / f"{args.out_prefix}-predictions.csv"
    lat_csv = HERE / f"{args.out_prefix}-latencies.csv"

    # Ensure master seed is present in MinIO and DB
    subprocess.run([sys.executable, str(HERE / "seed_science.py")], check=True)

    if args.fresh:
        for p in (pred_csv, lat_csv):
            try:
                p.unlink()
            except FileNotFoundError:
                pass

    with open(corpus, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    done = {}
    try:
        with open(pred_csv, newline="", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                done[r["message"]] = r["predicted"]
    except FileNotFoundError:
        pass

    with open(pred_csv, "a", newline="", encoding="utf-8") as pf, open(
        lat_csv, "a", newline="", encoding="utf-8"
    ) as lf:
        pw = csv.writer(pf)
        lw = csv.writer(lf)
        if not done:
            pw.writerow(["message", "truth", "predicted"])
            lw.writerow(["message", "truth", "predicted", "latency_ms"])
        for i, r in enumerate(rows, 1):
            if r["utterance"] in done:
                continue
            # Fresh user per row: zero history, no cross-row contamination.
            user = f"rq1-u-{uuid.uuid4().hex[:8]}"
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
    print("SWEEP COMPLETE", flush=True)


if __name__ == "__main__":
    sys.exit(main())
