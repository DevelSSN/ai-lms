#!/usr/bin/env python3
"""Shared infrastructure access for RQ2-RQ5 evaluation sweeps.

Contracts mirror the deployed AI-LMS ports/config (application.properties):
  - orchestrator HTTP  :10082/api/v1/orchestrate
  - Postgres (podman)  :7432  (podman exec postgres psql -U ailms -d ailms)
  - MinIO              :19000 bucket ailms-content (SigV4, stdlib only)
  - Qdrant REST        :10633 collection ailms-content (vector 768, cosine)
  - Ollama             :11434 (nomic-embed-text / llama3.2:3b)

Retrieval emulates the deployed pipeline (VectorDBService.search): take the
top (k*OVERFETCH) candidates by score, drop any below ailms.rag.min-score
(0.5), then keep k — so list agreement and P@k are measured at the same
operating point the deployed agents see.

Everything is stdlib; endpoints can be overridden with AILMS_* env vars so the
same scripts run locally or on the remote numbers box.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.request

ORCHESTRATE = os.environ.get("AILMS_ORCHESTRATE", "http://localhost:10082/api/v1/orchestrate")
OLLAMA = os.environ.get("AILMS_OLLAMA", "http://localhost:11434")
EMBED_MODEL = os.environ.get("AILMS_EMBED_MODEL", "nomic-embed-text")
CHAT_MODEL = os.environ.get("AILMS_CHAT_MODEL", "llama3.2:3b")
    # NOTE: sle-paper Table 5 fixes the deployed model at llama3.2:3b, T=0.

MINIO = os.environ.get("AILMS_MINIO", "http://localhost:19000")
MINIO_BUCKET = os.environ.get("AILMS_MINIO_BUCKET", "ailms-content")
MINIO_ACCESS = os.environ.get("AILMS_MINIO_ACCESS", "ailms")
MINIO_SECRET = os.environ.get("AILMS_MINIO_SECRET", "minio123")
MINIO_REGION = "us-east-1"

QDRANT = os.environ.get("AILMS_QDRANT_REST", "http://localhost:10633")
QDRANT_COLLECTION = os.environ.get("AILMS_QDRANT_COLLECTION", "ailms-content")
QDRANT_API_KEY = os.environ.get("AILMS_QDRANT_API_KEY", "qdrant")
PG_CONTAINER = os.environ.get("AILMS_PG_CONTAINER", "postgres")
PG_USER = os.environ.get("AILMS_PG_USER", "ailms")
PG_DB = os.environ.get("AILMS_PG_DB", "ailms")

OVERFETCH = 3          # VectorDBService 3x candidate overfetch
MIN_SCORE = 0.5        # ailms.rag.min-score, matching application.properties
QDRANT_VECTOR_SIZE = 768


# ---------------------------------------------------------------------------
# MinIO (SigV4, stdlib only — mirrors seed_science.py)
# ---------------------------------------------------------------------------

def _sign(key, msg):
    import hashlib
    import hmac
    return hmac.new(key, msg.encode(), hashlib.sha256).digest()


def _sigv4(method: str, key: str, payload_hash: str) -> dict:
    import hashlib
    import hmac
    from datetime import datetime, timezone
    now = datetime.now(timezone.utc)
    amzdate = now.strftime("%Y%m%dT%H%M%SZ")
    datestamp = now.strftime("%Y%m%d")
    host = MINIO.removeprefix("http://").removeprefix("https://")
    headers = {"host": host, "x-amz-content-sha256": payload_hash, "x-amz-date": amzdate}
    signed = ";".join(sorted(headers))
    canonical_headers = "".join(f"{k}:{headers[k]}\n" for k in sorted(headers))
    resource = f"/{MINIO_BUCKET}/{path_encode(key)}"
    canonical = "\n".join([method, resource, "", canonical_headers, signed, payload_hash])
    scope = f"{datestamp}/{MINIO_REGION}/s3/aws4_request"
    to_sign = "\n".join(["AWS4-HMAC-SHA256", amzdate, scope, hashlib.sha256(canonical.encode()).hexdigest()])
    k = _sign(f"AWS4{MINIO_SECRET}".encode(), datestamp)
    k = _sign(k, MINIO_REGION)
    k = _sign(k, "s3")
    k = _sign(k, "aws4_request")
    sig = hmac.new(k, to_sign.encode(), hashlib.sha256).hexdigest()
    return {
        "Authorization": f"AWS4-HMAC-SHA256 Credential={MINIO_ACCESS}/{scope}, SignedHeaders={signed}, Signature={sig}",
        "x-amz-content-sha256": payload_hash,
        "x-amz-date": amzdate,
    }


def minio_put(key: str, payload: bytes, content_type: str) -> int:
    import hashlib
    import urllib.request
    payload_hash = hashlib.sha256(payload).hexdigest()
    auth = _sigv4("PUT", key, payload_hash)
    req = urllib.request.Request(
        f"{MINIO}/{MINIO_BUCKET}/{path_encode(key)}", data=payload, method="PUT"
    )
    for name, value in auth.items():
        req.add_header(name, value)
    if content_type:
        req.add_header("content-type", content_type)
    with urllib.request.urlopen(req, timeout=300) as r:
        return r.status


def minio_head(key: str) -> int:
    import urllib.error
    import urllib.request
    try:
        auth = _sigv4("HEAD", key, "UNSIGNED-PAYLOAD")
        req = urllib.request.Request(f"{MINIO}/{MINIO_BUCKET}/{path_encode(key)}", method="HEAD")
        for name, value in auth.items():
            req.add_header(name, value)
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


def path_encode(key: str) -> str:
    from urllib.parse import quote
    return quote(key, safe="/-_.~")


# ---------------------------------------------------------------------------
# Postgres
# ---------------------------------------------------------------------------

def psql(sql: str, timeout: int = 60) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["podman", "exec", PG_CONTAINER, "psql", "-U", PG_USER, "-d", PG_DB, "-t", "-A", "-F", ",", "-c", sql],
        capture_output=True,
        text=True,
        timeout=timeout,
    )


def psql_stdin(sql: str, timeout: int = 900) -> subprocess.CompletedProcess:
    """Run psql with SQL on stdin.

    Large statements (e.g. a 500-row vector INSERT) exceed the kernel
    ARG_MAX when passed as a `-c` argument to podman exec (E2BIG), so bulk
    writes stream the SQL through stdin instead.
    """
    return subprocess.run(
        ["podman", "exec", "-i", PG_CONTAINER, "psql", "-U", PG_USER, "-d", PG_DB, "-t", "-A", "-F", ","],
        input=sql,
        capture_output=True,
        text=True,
        timeout=timeout,
    )


_PG_COLUMN_CACHE: dict[tuple[str, str], str] = {}


def pg_column(table: str, logical: str) -> str:
    """Resolve the physical column backing an entity field.

    Tolerates either snake_case (Hibernate 6 / Quarkus 3) or all-lowercase
    (Hibernate 5) physical naming so the same scripts run against either schema.
    """
    key = (table, logical)
    if key in _PG_COLUMN_CACHE:
        return _PG_COLUMN_CACHE[key]

    r = psql(
        "SELECT column_name FROM information_schema.columns "
        f"WHERE table_name = '{table}' ORDER BY ordinal_position;"
    )
    if r.returncode != 0:
        raise RuntimeError(f"column probe failed for {table}: {r.stderr.strip()}")
    cols = [ln.strip() for ln in r.stdout.splitlines() if ln.strip()]

    snake = re.sub(r"(?<!^)(?=[A-Z])", "_", logical).lower()
    candidates = {logical.lower(), snake}
    matches = [c for c in candidates if c in cols]
    if len(matches) != 1:
        raise RuntimeError(
            f"cannot resolve {table}.{logical}: saw columns {cols}, "
            f"wanted one of {sorted(candidates)}"
        )
    _PG_COLUMN_CACHE[key] = matches[0]
    return matches[0]


def pg_count(query) -> int:
    r = psql(query)
    if r.returncode != 0:
        raise RuntimeError(f"psql failed: {r.stderr.strip()}")
    rows = [ln for ln in r.stdout.splitlines() if ln.strip()]
    return len(rows)


def pg_vector_k(query_vector: list[float], k: int, table: str = "content_embeddings") -> list[dict]:
    """Top-k from pgvector by cosine distance, mirroring the deployed retriever."""
    return pg_ranked(query_vector, k, table)[:k]


def pg_ranked(query_vector: list[float], k: int, table: str = "content_embeddings") -> list[dict]:
    """Candidates (top k*OVERFETCH) from pgvector, min-score filtered, full order.

    `table` selects the embedding table so chunk-ablation arms can read their own
    scratch table instead of polluting the deployed content_embeddings.
    """
    vec = "[" + ",".join(repr(float(x)) for x in query_vector) + "]"
    doc_col = pg_column(table, "documentId")
    sql = (
        f"SELECT source, {doc_col}, (1 - (embedding <=> '{vec}'::vector)) AS score "
        f"FROM {table} ORDER BY embedding <=> '{vec}'::vector LIMIT {k * OVERFETCH};"
    )
    r = psql(sql)
    if r.returncode != 0:
        raise RuntimeError(f"pgvector search failed ({table}): {r.stderr.strip()}")
    scored = []
    for line in r.stdout.splitlines():
        parts = line.split(",", 2)
        if len(parts) != 3:
            continue
        source, doc_id, score = parts
        try:
            scored.append({"source": source, "doc_id": doc_id, "score": float(score)})
        except ValueError:
            continue
    return _apply_filter_only(scored)


# ---------------------------------------------------------------------------
# Qdrant (REST)
# ---------------------------------------------------------------------------

def _qdrant_headers() -> dict:
    headers = {"Content-Type": "application/json"}
    if QDRANT_API_KEY:
        headers["api-key"] = QDRANT_API_KEY
    return headers


def qdrant_count(collection: str, query_filter: dict) -> int:
    """Point count in `collection` matching a Qdrant filter (e.g. source key)."""
    body = json.dumps({"filter": query_filter}).encode()
    req = urllib.request.Request(
        f"{QDRANT}/collections/{collection}/points/count",
        data=body,
        headers=_qdrant_headers(),
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)["result"]["count"]


def qdrant_search(query_vector: list[float], k: int, collection: str = QDRANT_COLLECTION) -> list[dict]:
    return qdrant_ranked(query_vector, k, collection)[:k]


def qdrant_ranked(query_vector: list[float], k: int, collection: str = QDRANT_COLLECTION) -> list[dict]:
    body = json.dumps(
        {"vector": query_vector, "limit": k * OVERFETCH, "with_payload": True}
    ).encode()
    req = urllib.request.Request(
        f"{QDRANT}/collections/{collection}/points/search",
        data=body,
        headers=_qdrant_headers(),
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        data = json.load(resp)
    scored = []
    for pt in data.get("result", []):
        scored.append(
            {
                "source": pt.get("payload", {}).get("source"),
                "doc_id": pt.get("payload", {}).get("doc_id"),
                "score": pt.get("score"),
            }
        )
    return _apply_filter_only(scored)


def qdrant_upsert(collection: str, points: list[dict]) -> None:
    body = json.dumps({"points": points}).encode()
    req = urllib.request.Request(
        f"{QDRANT}/collections/{collection}/points?wait=true",
        data=body,
        headers=_qdrant_headers(),
        method="PUT",
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        json.load(resp)


def qdrant_recreate_collection(collection: str, size: int = QDRANT_VECTOR_SIZE) -> None:
    body = json.dumps({"vectors": {"size": size, "distance": "Cosine"}}).encode()
    for method, url in (
        ("DELETE", f"{QDRANT}/collections/{collection}"),
        ("PUT", f"{QDRANT}/collections/{collection}"),
    ):
        headers = _qdrant_headers() if method == "PUT" else None
        if method == "DELETE" and QDRANT_API_KEY:
            headers = {"api-key": QDRANT_API_KEY}
        req = urllib.request.Request(url, data=body if method == "PUT" else None,
                                     headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                resp.read()
        except urllib.error.HTTPError as e:
            if e.code != 404:  # 404 on DELETE is fine (did not exist)
                raise


def _apply_operating_filter(scored: list[dict], k: int) -> list[dict]:
    """Deployed pipeline: overfetch*3 candidates -> drop score<min -> keep k."""
    return _apply_filter_only(scored)[:k]


def _apply_filter_only(scored: list[dict]) -> list[dict]:
    """Deployed pipeline: drop any candidate below min-score, preserve rank order."""
    return [s for s in scored if s["score"] is None or s["score"] >= MIN_SCORE]


def pg_upsert_vectors(table: str, rows: list[tuple[str, str, str, list[float]]]) -> None:
    """Bulk upsert (id, source, document_id, embedding) into a scratch embedding table."""
    create = (
        f"CREATE TABLE IF NOT EXISTS {table} (id uuid PRIMARY KEY, source text, "
        "document_id text, embedding vector(768));"
    )
    r = psql_stdin(create)
    if r.returncode != 0:
        raise RuntimeError(f"pg table create failed ({table}): {r.stderr.strip()}")
    values = []
    for doc_uuid, source, doc_id, vec in rows:
        v = "[" + ",".join(repr(float(x)) for x in vec) + "]"
        values.append(f"('{doc_uuid}', '{source.replace(chr(39), chr(39)+chr(39))}', "
                      f"'{doc_id.replace(chr(39), chr(39)+chr(39))}', '{v}'::vector)")
    for start in range(0, len(values), 500):
        batch = ",\n".join(values[start:start + 500])
        r = psql_stdin(f"INSERT INTO {table} (id, source, document_id, embedding) VALUES {batch} "
                       "ON CONFLICT (id) DO NOTHING;")
        if r.returncode != 0:
            raise RuntimeError(f"pg upsert failed ({table}): {r.stderr.strip()}")


# ---------------------------------------------------------------------------
# Ollama
# ---------------------------------------------------------------------------

def ollama_embed(text: str, model: str = EMBED_MODEL) -> list[float]:
    body = json.dumps({"model": model, "input": text}).encode()
    req = urllib.request.Request(
        f"{OLLAMA}/api/embed", data=body, headers={"Content-Type": "application/json"}, method="POST"
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = json.load(resp)
    return data["embeddings"][0]


def ollama_embed_many(texts: list[str], model: str = EMBED_MODEL, batch: int = 64) -> list[list[float]]:
    """Batch-embed a list of texts via a single /api/embed request per batch.

    One HTTP call per <=`batch` inputs instead of one call per chunk — the
    dominant cost is request round-trips, not the model. If a model rejects
    list input (embedding count mismatch), degrades to sequential embeds for
    that batch so vectors stay identical to the deployed single-input path.
    """
    out: list[list[float]] = []
    for start in range(0, len(texts), batch):
        chunk = texts[start:start + batch]
        embs: list[list[float]] = []
        try:
            body = json.dumps({"model": model, "input": chunk}).encode()
            req = urllib.request.Request(
                f"{OLLAMA}/api/embed", data=body, headers={"Content-Type": "application/json"}, method="POST"
            )
            with urllib.request.urlopen(req, timeout=900) as resp:
                data = json.load(resp)
            embs = data.get("embeddings") or []
        except (urllib.error.URLError, OSError, json.JSONDecodeError, KeyError):
            embs = []
        if len(embs) != len(chunk):
            embs = [ollama_embed(t, model) for t in chunk]
        out.extend(embs)
    return out


def ollama_chat(prompt: str, model: str = CHAT_MODEL, system: str | None = None) -> str:
    """One-shot non-streaming chat completion; full response text."""
    messages = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})
    body = json.dumps({"model": model, "messages": messages,
                       "options": {"temperature": 0.0}, "stream": False}).encode()
    req = urllib.request.Request(
        f"{OLLAMA}/api/chat", data=body, headers={"Content-Type": "application/json"}, method="POST"
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        data = json.load(resp)
    return data.get("message", {}).get("content", "")


def ollama_models() -> list[str]:
    try:
        with urllib.request.urlopen(f"{OLLAMA}/api/tags", timeout=5) as resp:
            return sorted(m["name"] for m in json.load(resp).get("models", []))
    except Exception:
        return []


def ollama_chat_stream(prompt: str, model: str = CHAT_MODEL) -> dict:
    """Time-to-first-token via the streaming /api/chat endpoint (T=0 fixed).

    Returns {ttft_s, first_token_s, tokens, elapsed_s} or propagates errors.
    """
    import time as _t
    body = json.dumps(
        {"model": model, "messages": [{"role": "user", "content": prompt}],
         "options": {"temperature": 0.0}, "stream": True}
    ).encode()
    req = urllib.request.Request(
        f"{OLLAMA}/api/chat", data=body, headers={"Content-Type": "application/json"}, method="POST"
    )
    started = _t.perf_counter()
    ttft = None
    first_token_s = None
    tokens = 0
    ndata = b""
    with urllib.request.urlopen(req, timeout=600) as resp:
        for chunk in iter(lambda: resp.read(4096), b""):
            ndata += chunk
            while b"\n" in ndata:
                line, ndata = ndata.split(b"\n", 1)
                if not line.strip():
                    continue
                msg = json.loads(line)
                content = msg.get("message", {}).get("content", "")
                if content:
                    if ttft is None:
                        ttft = _t.perf_counter() - started
                        first_token_s = content  # exact first token text
                    tokens += 1
    return {"ttft_s": ttft, "first_token_s": first_token_s,
            "tokens": tokens, "elapsed_s": _t.perf_counter() - started}


# ---------------------------------------------------------------------------
# Orchestrator (drives deployed decision logic / agents)
# ---------------------------------------------------------------------------

def orchestrate(message: str, session_id: str, user_id: str, bypass: bool = False, timeout: int = 600) -> dict:
    body = json.dumps(
        {"message": message, "sessionId": session_id, "bypassRoutes": bypass}
    ).encode()
    req = urllib.request.Request(
        ORCHESTRATE, data=body, headers={"Content-Type": "application/json", "X-User-Id": user_id}, method="POST"
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.load(resp)


def wait_for(predicate, what: str, timeout_s: int = 900, poll_s: float = 5.0):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        if predicate():
            return True
        time.sleep(poll_s)
    raise TimeoutError(f"timed out waiting for {what}")