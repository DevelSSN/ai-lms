#!/usr/bin/env python3
"""Ingest the RQ2/RQ4 corpus through the deployed pipeline and verify dual-write.

For each PDF in evaluation/files/:
  1. MinIO PUT into bucket ailms-content (SigV4), key uploads/ailms-eval/<doc>.
  2. SQL INSERT a ContentDocument (user/eval-user, status UPLOADED).
  3. Trigger the deployed parse -> boundary-snapped chunk -> dual-write path by
     POSTing the upload-analysis prefix message to the orchestrator, exactly as
     the gateway upload flow does ("Analyze the uploaded file: <docId>").
  4. Wait for status=INDEXED and ==1 pgvector rows, then verify the Qdrant point
     count equals the pgvector chunk count (dual-write parity).

Writes datasets/corpus-manifest.json: {doc_id: {filename, qdrant_chunks,
pgvector_chunks, filetype}} which rq2_retrieval.py uses to map retrieved
source keys (doc:<docId>) back to the filenames named in the qrels.

Usage:
  python3 evaluation/ingest_corpus.py                # union the whole files/ corpus
  python3 evaluation/ingest_corpus.py --file Science.pdf   # single document
"""
from __future__ import annotations

import argparse
import json
import re
import uuid
from pathlib import Path

import infra

HERE = Path(__file__).resolve().parent
FILES = HERE / "files"
MANIFEST = HERE / "datasets" / "corpus-manifest.json"
UPLOAD_PREFIX = "Analyze the uploaded file: "  # = PromptPrefixes.UPLOAD_ANALYSIS
PDF_TYPE = "application/pdf"


def doc_id_for(filename: str) -> str:
    slug = re.sub(r"[^A-Za-z0-9]+", "-", Path(filename).stem).strip("-").lower()
    return f"evl-{slug}"


def seed_doc(filename: str) -> tuple[str, str]:
    data = (FILES / filename).read_bytes()
    doc_id = doc_id_for(filename)
    key = f"uploads/ailms-eval/{doc_id}/{filename}"
    if infra.minio_head(key) != 200:
        infra.minio_put(key, data, PDF_TYPE)
        print(f"  MinIO PUT {key} ({len(data)} bytes)")
    else:
        print(f"  MinIO key present, skipping: {key}")

    sql = (
        "INSERT INTO content_documents (id, userid, sessionid, filename, filetype, filesize,"
        f" storagepath, status, uploadedat) VALUES ('{doc_id}', 'eval-user', 'eval-ingest',"
        f" '{filename.replace(chr(39), chr(39)+chr(39))}', '{PDF_TYPE}', {len(data)},"
        f" '{key.replace(chr(39), chr(39)+chr(39))}', 'UPLOADED', now())"
        " ON CONFLICT (id) DO NOTHING;"
    )
    r = infra.psql(sql)
    if r.returncode != 0:
        raise RuntimeError(f"seed INSERT failed for {filename}: {r.stderr.strip()}")
    return doc_id, key


def trigger_ingest(doc_id: str, filename: str) -> None:
    full_id = doc_id  # message must carry the ContentDocument id AFTER the prefix
    response = infra.orchestrate(
        UPLOAD_PREFIX + full_id, session_id="eval-ingest", user_id="eval-user"
    )
    # enrichUploadAnalysis ingests synchronously inside the route call; a 200
    # response implies parse+chunk+dual-write left the request handler.
    print(f"  orchestrator ingest trigger OK (doc={full_id})")


def _pg_int(sql: str) -> int | None:
    r = infra.psql(sql)
    if r.returncode != 0:
        return None
    rows = [ln for ln in r.stdout.splitlines() if ln.strip()]
    return int(rows[0]) if rows else None


def verify(doc_id: str, filename: str) -> dict:
    def rows_indexed() -> bool:
        return (_pg_int(
            f"SELECT count(*) FROM content_embeddings WHERE document_id = '{doc_id}';"
        ) or 0) > 0

    infra.wait_for(rows_indexed, f"pgvector rows for {doc_id}")
    r = infra.psql(f"SELECT status FROM content_documents WHERE id = '{doc_id}';")
    status = (r.stdout.strip().splitlines()[0] if r.returncode == 0 and r.stdout.strip() else "UNKNOWN")
    pgv = _pg_int(
        f"SELECT count(*) FROM content_embeddings WHERE document_id = '{doc_id}';"
    ) or 0

    source = f"doc:{doc_id}"
    body = json.dumps({"filter": {"must": [{"key": "source", "match": {"value": source}}]}}).encode()
    import urllib.request
    req = urllib.request.Request(
        f"{infra.QDRANT}/collections/{infra.QDRANT_COLLECTION}/points/count",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        qdrant_count = json.load(resp)["result"]["count"]

    if status != "INDEXED":
        raise RuntimeError(f"{doc_id} ended in status {status}, expected INDEXED")
    if qdrant_count != pgv:
        raise RuntimeError(
            f"{doc_id} dual-write parity failed: qdrant={qdrant_count} pgvector={pgv}"
        )
    print(f"  {doc_id}: INDEXED, chunks qdrant={qdrant_count} pgvector={pgv} (parity OK)")
    return {"filename": filename, "status": status, "qdrant_chunks": qdrant_count, "pgvector_chunks": pgv, "filetype": PDF_TYPE}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", help="ingest a single file from evaluation/files/")
    ap.add_argument("--collection", default=infra.QDRANT_COLLECTION,
                    help="Qdrant collection for the ingest trigger (for RQ4 variants)")
    args = ap.parse_args()

    files = sorted(p.name for p in FILES.glob("*.pdf"))
    if args.file:
        if args.file not in files:
            raise SystemExit(f"{args.file!r} not among corpus files: {files}")
        files = [args.file]

    existing = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {}
    for filename in files:
        print(filename)
        doc_id, _ = seed_doc(filename)
        trigger_ingest(doc_id, filename)
        existing[doc_id] = verify(doc_id, filename)
    MANIFEST.write_text(json.dumps(existing, indent=2) + "\n")
    print(f"\nwrote {MANIFEST} ({len(existing)} documents)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())