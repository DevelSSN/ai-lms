#!/usr/bin/env python3
"""Seed the corpus-wide SeedHandbook.pdf into MinIO with stdlib-only SigV4.

Replaces Science.pdf as the RQ1 seed so retrieval legitimately clears
applyDocumentGate for every topic in the labelled corpus, instead of relying on
the MATERIAL_POINTER whitelist matching the user's phrasing.

Uploads evaluation/seed/SeedHandbook.pdf to ailms-content/uploads/rq1-seeded/seed-handbook.pdf
and inserts the matching ContentDocument row for user rq1-seeded via podman psql.
Idempotent: skips upload if key exists, INSERT uses ON CONFLICT DO NOTHING.
"""
import hashlib
import hmac
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
PDF = HERE / "seed" / "SeedHandbook.pdf"
MINIO = "http://localhost:19000"
BUCKET = "ailms-content"
KEY = "uploads/rq1-seeded/seed-handbook.pdf"
DOC_ID = "rq1-handbook-doc"
FILENAME = "SeedHandbook.pdf"
USER_ID = "rq1-seeded"
SESSION_ID = "rq1-seed"
ACCESS = "ailms"
SECRET = "minio123"
REGION = "us-east-1"
SERVICE = "s3"


def sign(key, msg):
    return hmac.new(key, msg.encode(), hashlib.sha256).digest()


def signed_request(method, path, payload=b"", content_type=""):
    now = datetime.now(timezone.utc)
    amzdate = now.strftime("%Y%m%dT%H%M%SZ")
    datestamp = now.strftime("%Y%m%d")
    payload_hash = hashlib.sha256(payload).hexdigest()
    host = "localhost:19000"
    headers = {
        "host": host,
        "x-amz-content-sha256": payload_hash,
        "x-amz-date": amzdate,
    }
    if content_type:
        headers["content-type"] = content_type
    signed = ";".join(sorted(headers))
    canonical_headers = "".join(f"{k}:{headers[k]}\n" for k in sorted(headers))
    canonical = "\n".join(
        [method, path, "", canonical_headers, signed, payload_hash]
    )
    scope = f"{datestamp}/{REGION}/{SERVICE}/aws4_request"
    to_sign = "\n".join(
        ["AWS4-HMAC-SHA256", amzdate, scope, hashlib.sha256(canonical.encode()).hexdigest()]
    )
    k = sign(f"AWS4{SECRET}".encode(), datestamp)
    k = sign(k, REGION)
    k = sign(k, SERVICE)
    k = sign(k, "aws4_request")
    sig = hmac.new(k, to_sign.encode(), hashlib.sha256).hexdigest()
    auth = (
        f"AWS4-HMAC-SHA256 Credential={ACCESS}/{scope}, "
        f"SignedHeaders={signed}, Signature={sig}"
    )
    req = urllib.request.Request(
        MINIO + path, data=payload if method == "PUT" else None, method=method
    )
    for k, v in headers.items():
        req.add_header(k, v)
    req.add_header("Authorization", auth)
    return req


def minio_head(path):
    try:
        with urllib.request.urlopen(signed_request("HEAD", path), timeout=30) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


def minio_put(path, payload, content_type):
    with urllib.request.urlopen(
        signed_request("PUT", path, payload, content_type), timeout=120
    ) as r:
        return r.status


def main():
    if not PDF.is_file():
        sys.exit(f"missing {PDF} -- build it with pandoc from SeedHandbook.md")
    data = PDF.read_bytes()
    obj_path = f"/{BUCKET}/{KEY}"
    head = minio_head(obj_path)
    if head == 200:
        print(f"MinIO key exists, skipping upload: {KEY}")
    else:
        status = minio_put(obj_path, data, "application/pdf")
        print(f"Uploaded {PDF.name} ({len(data)} bytes) -> {KEY} (HTTP {status})")

    sql = (
        "INSERT INTO content_documents (id, userid, sessionid, filename, filetype,"
        f" filesize, storagepath, status, uploadedat) VALUES ('{DOC_ID}',"
        f" '{USER_ID}', '{SESSION_ID}', '{FILENAME}', 'application/pdf', {len(data)},"
        f" '{KEY}', 'UPLOADED', now()) ON CONFLICT (id) DO NOTHING;"
    )
    r = subprocess.run(
        ["podman", "exec", "postgres", "psql", "-U", "ailms", "-d", "ailms", "-c", sql],
        capture_output=True,
        text=True,
    )
    print(r.stdout.strip() or r.stderr.strip())
    if r.returncode != 0:
        sys.exit(f"DB seed failed (rc={r.returncode})")
    print("SEED COMPLETE")


if __name__ == "__main__":
    sys.exit(main())
