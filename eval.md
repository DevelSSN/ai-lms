# AI-LMS Evaluation Plan (RQ1–RQ4)

Execution plan to obtain all hard numbers for the paper's §Evaluation tables
(`tab:evalsetup`, `tab:rq1`–`tab:rq4`) and the abstract/conclusion `%TODO-RESULTS`
slots in `paper.tex` (MP-All/paper.tex).

> Rule: **No number is fabricated.** Every cell is populated only from an
> instrumented run under fixed conditions. If a run cannot complete, the section
> must be demoted to a protocol-only section — never submit empty `---` results.

---

## Phase 0 — Readiness (blocks everything)

### 0.1 Start real infrastructure
Infra is not in compose; Ollama runs separately.

```bash
cd MP/infra
cp ../.env.example .env
podman compose up -d postgres redis kafka keycloak qdrant minio
```

### 0.2 Install models into Ollama
Not in compose — must be reachable at the configured endpoint.

```bash
ollama pull deepseek-r1:7b
ollama pull nomic-embed-text
```

### 0.3 Fix code bugs that block/contaminate runs
From `MP/TODO.md` deep code audit. These MUST be fixed before measuring, or the
numbers are invalid.

- **A2 / C2 — pgvector type mapping:** `ContentEmbedding` `vector(768)` has no
  Hibernate pgvector type mapping → first real persist fails. Add
  `quarkus-jdbc-postgresql` to `common` + the type mapping; run on Postgres, not H2.
- **A3 — Qdrant init:** `QdrantInitializer` reads nonexistent `qdrant.rest.*` keys;
  port 10633 vs gRPC 10634 mismatch → collection init silently fails. Fix keys to
  `quarkus.langchain4j.qdrant.*`, single port source of truth.
- **A5 — routing short-circuits:** `OrchestratorService.java:141,148` — `youtube`
  keyword force-routes to `VIDEO_SEARCH` and `DOC_REFERENCE` regex matches normal
  phrases. **Remove** — the paper claims the LLM decides except explicit links.
- **A1 — Redis cache:** `||` delimiter corrupts reads (memory-path realism).
- **A6 — ResponseVerifier:** fail-open on LLM exception + fragile `contains` parse.

### 0.4 Verify a clean end-to-end run
```bash
./mvnw clean install
# start orchestrator + api-gateway, upload a test document, confirm it reaches index
```

---

## Phase 1 — Evaluation harness & corpus (shared by RQ1/RQ2)

There is currently **no** labeled utterance set, gold relevance set, or document
corpus. Build them.

### B1. Labeled utterance set (RQ1, RQ4-iv)
- Intent names VERBATIM from `IntentType.java:3-8`:
  `CONVERSATION, VIDEO_SEARCH, CONTENT_ANALYSIS, ASSESSMENT, INSIGHT`.
- Author ≥200 natural-language utterances: true positives, obvious short-circuits
  (greetings → CONVERSATION, bare YouTube URLs → VIDEO_SEARCH), boundary/adversarial
  cases across all 5 classes.
- Store as test resource, e.g. `src/test/resources/eval/utterances.csv`
  (`intent,message`). Ground truth = author-scribed expected intent.

### B2. Document corpus (RQ2, RQ3, RQ4-i)
- Assemble educational documents (PDF/text/slides) uploaded through the standard
  ingestion pipeline. Record corpus size in docs/chunks.

### B3. Gold relevance judgment set (RQ2)
- For a query set, two independent annotators judge each retrieved chunk
  relevant/not.
- Compute inter-annotator agreement as **Cohen's κ** between the two annotators.

---

## Phase 2 — Instrumentation (routing counts + time/latency)

No metrics code exists. Add a dedicated harness (either a `@RequestScoped`
`EvaluationCollector` or per-RQ harness class), ideally guarded by a profile flag
to avoid polluting production.

Instrument these sites (from code audit):

| Measurement | Instrument at | File:Line |
|---|---|---|
| Routing short-circuit hit rate (greeting) | `isBareGreeting` | `OrchestratorService.java:146` |
| Routing short-circuit hit rate (video link) | `isExplicitVideoLink` | `OrchestratorService.java:162` |
| Classifier LLM latency | `intentClassifier.classify()` | `OrchestratorService.java:171` |
| RAG retrieval latency | query embed + `embeddingStore.search()` | `VectorDBService.java:104,113` |
| Tika extraction latency | `parser.parse()` | `DocumentParserService.java:37` |
| Embedding (ingest) latency | `embeddingModel.embed()` | `VectorDBService.java:71` |
| Agent LLM first-token | LangChain4j streaming callback/interceptor (none exists — add) | new |
| End-to-end total | `processMessage()` | `OrchestratorResource.java:36-48` |
| Raw retrieval results (for offline precision@k etc.) | before/after filters | `VectorDBService.java:113-121` |

Run under **fixed load**, record p50/p95/p99 per stage → `tab:rq3`.

---

## Phase 3 — RQ4 ablation variants

Run each ablation under **identical corpus and load**.

- **RQ4-i chunk size:** parameterize `OrchestratorService.java:123-125`
  (`CHUNK_SIZE=800`, `CHUNK_OVERLAP=100`) and `chunkText()`
  (`ContentDocumentService.java:107-121`); run 400/50, 800/100, 1600/200 → score by
  retrieval precision → `tab:rq4` rows 1–3.
- **RQ4-ii oEmbed filter:** toggle YouTube oEmbed post-validation
  (`YouTubeLinkValidator.java:38-107`) on/off; score fabricated-link catch rate on
  adversarial prompts → rows 4–5.
- **RQ4-iii think-stripping:** toggle `TextUtils.stripThinking()`
  (`OrchestratorService.java:194,512`) on/off; measure malformed-output incidence →
  row 6.
- **RQ4-iv few-shot vs zero-shot:** `IntentClassifier.java:17-70` — few-shot prompt
  is lines 28–67; strip for the zero-shot arm (keep label list + instruction line 68).
  Measure accuracy delta → last row.

---

## Phase 4 — Run sweeps & populate the paper

### RQ1 → `tab:rq1`
Run labeled set through routing. Per-intent share %, precision/recall/F1
(one-vs-rest), overall accuracy, router-served fraction, median routing latency
saved (deterministic vs classifier), monolithic-alternative median latency (all via
classifier).

### RQ2 → `tab:rq2`
At k=8 and k=3: precision@k, recall@k, MRR over the gold set; report Cohen's κ and
Qdrant–pgvector list agreement.

### RQ3 → `tab:rq3`
Stage timers → p50/p95/p99 per stage.

### RQ4 → `tab:rq4`
The four ablations above.

### Write into paper.tex
1. Fill `tab:evalsetup` (CPU, RAM, GPU, OS, JVM/native mode, corpus docs/chunks,
   labeled-set count).
2. Fill `tab:rq1`–`tab:rq4`.
3. Insert headline numbers into abstract `%TODO-RESULTS` (`paper.tex:24`) and
   Conclusion `%TODO-RESULTS` (`paper.tex:449`) — keep abstract ≤250 words.
4. Convert the Evaluation closing paragraph (`paper.tex:296`) to factual past tense
   once numbers are real.
5. Delete all `%TODO-MEASURE` ×6 and `%TODO-RESULTS` ×2 markers; retitle the
   "(Pending)" captions (`paper.tex:302,333,365,386,407`).
6. Rebuild: `pdflatex → biber → pdflatex ×2`. Verify 0 errors, 0 overfull boxes,
   0 undefined refs, and **no `---` remains in any result cell** (grep tables).

---

## Key risks / tradeoffs

- **Correctness gate:** Phase 0.3 bugs must be fixed first or all numbers are
  invalid. A5's YouTube/DOC_REFERENCE short-circuits falsify the RQ1 classifier
  share.
- **First-token (RQ3):** needs a new streaming-callback interceptor. If infeasible,
  drop the "LLM first token" row from `tab:rq3` — never leave it empty.
- **Corpus/gold set (B1–B3):** largest manual effort; paper promises ≥200
  utterances and a two-annotator gold set.
- **Fallback:** if runs cannot complete before deadline, demote §Evaluation to a
  protocol-only section and strip empty tables (per S15). Never submit empty result
  cells.
