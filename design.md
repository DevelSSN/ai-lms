# AI-LMS (AILMS) — Project Design & Architecture

> Working branch: `shashank/hardening`.
> This document reflects the **current running system** (Quarkus 3.37.2 / LangChain4j agents / Ollama), not just the original intent.

## Architecture Flow

```mermaid
flowchart TD
  U[User / Client App] -->|OIDC| API[API Gateway :8080]
  API --> CHAT[ChatResource<br/>REST + upload]
  API --> SSE[/api/updates<br/>SSE Stream]
  API --> RA[InteractResource<br/>@RolesAllowed]

  CHAT -->|upload| OBJ[(Object Storage S3/MinIO)]
  CHAT -->|routeAsync| ORCH[LLM Orchestrator :8082]

  ORCH --> ROUTE[Router<br/>intent classify]
  ROUTE --> CA[Conversation Agent]
  ROUTE --> CAA[Content Analysis Agent]
  ROUTE --> QGA[Question Generation Agent]
  ROUTE --> VER[Response Verifier Agent]
  ROUTE --> PA[Profiling Agent]
  ROUTE --> IRA[Insight Agent]
  ROUTE --> VSA[Video Search Agent]

  CAA --> VDB[(Vector DB: Qdrant + pgvector)]
  QGA -->|ollama-quiz model<br/>format=json| OLLAMA((Ollama))
  CAA -->|analysis ctx| QGA

  VER -->|accept / NEEDS_REWRITE| ROUTE
  QGA -->|raw JSON array| VER

  ORCH --> STM[(Redis: memory + history cache)]
  ORCH --> PDB[(PostgreSQL: users, logs, docs, quizzes)]

  ORCH -->|content-analysis-complete| KAFKA[(Kafka)]
  PRO[Proactive Agent<br/>Quarkus @Scheduled 5m] --> KAFKA
  KAFKA -->|proactive-events| SSEB[SseEventBridge]
  ORCH --> SRC[Response Composer]

  SSEB -->|broadcast user_id scoped| SSE
  SRC --> CHAT
  SSE --> U
  CHAT --> U
  U -->|Generate Quiz / ask| CHAT
```

## Tech Stack
- **Runtime:** Java 25 (GraalVM CE) + Quarkus 3.37.2
- **Build:** Maven 3.9+ multi-module
- **DB:** PostgreSQL 18 (User Profile / Conversation / Content / QuizResult) + pgvector
- **Vector DB:** Qdrant (admin REST :10633, gRPC via LangChain4j :10634)
- **Cache:** Redis 8 (chat memory namespaces + history cache)
- **Messaging:** Apache Kafka 4.3 (content-analysis-complete, proactive-events, dlq-*)
- **LLM:** Ollama (default model, plus named `ollama-quiz` model returning strict JSON)
- **Auth:** Keycloak 26 + OIDC, realm roles `STUDENT`/`TEACHER`/`ADMIN`
- **Container:** Docker Compose

## Project Modules

| Module | Artifact | Port | Description |
|--------|----------|------|-------------|
| `common` | `ailms-common` | — | Shared entities, DTOs, constants, enums (`ChatRole`, `ContentStatus`) |
| `api-gateway` | `ailms-api-gateway` | 8080 | REST API, OIDC auth + RBAC, upload, SSE, analytics |
| `orchestrator` | `ailms-orchestrator` | 8082 | LLM orchestration, 7 AI-service agents, vector ingest |

### System Components

#### 1. API Gateway (`api-gateway/`)
Single entry point. Owns OIDC/Keycloak auth with `@RolesAllowed` on every resource, file upload (S3 fast path),
the `/api/updates` SSE stream (per-user emitters, reconnect/backoff client), and analytics endpoints.

#### 2. LLM Orchestrator (`orchestrator/`)
Central coordinator:
- `routeAsync()` → returns `PENDING` + `sessionId` immediately; runs `route()` on a plain executor (request context + JTA) via `AsyncJobRunner`
- Intent classification (LLM) → dispatch to a specialized agent
- Session/thread ownership enforcement (`sessionOwner` check) before processing
- Aggregates context from Vector DB (Qdrant + pgvector) and analyzed-document memory
- Persists every user + assistant message through `ConversationRepository.logMessage` (DB + Redis history cache)

#### 3. Agents (AI Services inside `orchestrator/`)
| Agent | Role |
|-------|------|
| Conversation Agent (CA) | Dialogue, friendly answers; citation markers from RAG chunks |
| Content Analysis Agent (CAA) | Document analysis → **structured analysis** (topic map), grounded in uploaded content |
| Question Generation Agent (QGA) | Quiz generation; runs on `ollama-quiz` named model |
| Response Verifier Agent | Judges answers; accepts grounded/structured, rejects off-topic/unanswered |
| Profiling Agent (PA) | User preference / knowledge-level extraction (gets raw student message only) |
| Insight Agent (IRA) | Progress reports, recommendations |
| Video Search Agent | YouTube handling (explicit-link short-circuit only) |
| Proactive Follow-Up Agent | Generates the friendly follow-up text for inactive users |

Supporting: `IntentClassifier` (LLM), `ResponseComposer`, `ResponseVerifierAgent`, `YouTubeLinkValidator` (fail-closed), `TextUtils` (strip thinking / extract JSON).

#### 4. Data Layer
- **PostgreSQL:** `ContentDocument` (status: UPLOADED→PARSED→INDEXED/FAILED), `ConversationLog` (soft-delete, role + agentType), `UserProfile`, `QuizResult`, `TeacherStudent`, `ContentEmbedding`
- **pgvector:** embeddings column alongside Qdrant (dual-write)
- **Qdrant:** `doc:<documentId>` / `video:<id>` sources; metadata-equality source filter pushed into search request
- **Redis:** agent memory namespaces (`conversation:`/`analysis:`/`assessment:`/`insight:`/`profiling:`) + JSON-serialized history cache (`CacheEntry` with timestamp)
- **Kafka:** `content-analysis-complete`, `proactive-events` (plus dead-letter topics)

## Runtime Flows

### Upload → Analysis → Quiz (the hero demo)
1. Client uploads a file → Gateway `uploadFile` stores it and returns a `documentId` (S3 fast path).
2. Gateway calls orchestrator `routeAsync` with `"Analyze the uploaded file: <docId>"` **in the current chat thread id**; orchestrator returns `PENDING` + `sessionId` immediately.
3. `runAnalysisJob` runs async: `route()` →
   - classify `CONTENT_ANALYSIS`; `enrichUploadAnalysis` reads the stored content, chunks it, embeds into Qdrant + pgvector (doc **stays** `PARSED`)
   - CAA produces the structured analysis; verifier judges it; `lastUsableAnswer` keeps grounded summaries and structured quizzes
   - transcript is persisted (`logMessage`), then `ContentDocumentService.markIndexed(docId)` — **INDEXED is flipped only after the transcript exists**, so any reload sees the analysis
   - Kafka `content-analysis-complete` → Gateway `SseEventBridge` → SSE push to that user
4. Client polls document status; on `INDEXED` it reloads `loadHistory(sessionId)` → renders the analysis and shows a "Generate Quiz" bar (agentType `CONTENT_ANALYSIS`), deduplicating against live SSE.
5. "Generate Quiz" → orchestrator `ASSESSMENT` intent → QGA on the `ollama-quiz` model (`format=json`, temperature 0) → raw JSON quiz; `enforceStructuredQuiz` regenerates once with a strict "raw JSON array only" instruction if the model returned prose.
6. Frontend renders interactive cards via `tryParseQuizJson` (works live **and** from persisted history). Answers/score flow into `QuizResult`.

### Real-time SSE delivery
- `/api/updates` keeps per-user emitters; events are broadcast with `user_id` and are skipped client-side when they don't match the current user.
- Client reconnects with exponential backoff (1s→30s), skips pings, and dedupes against the last rendered bot message.
- Live transcript is intentionally non-polluting: transient "uploading / analyzing" states are an ephemeral `.processing-chip`, never persisted messages.

### Proactive follow-up
- Quarkus `@Scheduled(every = "5m")` scans for users whose last conversation is older than `ailms.proactive.inactivity-cutoff` (default `15m` for dev, override with `AILMS_PROACTIVE_INACTIVITY_CUTOFF`).
- For each eligible user it persists an assistant follow-up **into the user's last session** (survives refresh) **and** publishes `ProactiveEvent` → Kafka → SSE live push.
- `lastProactiveSentAt` guard prevents re-pinging until the cutoff lapses again.

### Structured, deterministic output
- Named LLM model `ollama-quiz` (`deepseek-r1:7b`, temperature 0, `format=json`) bound via `@RegisterAiService(modelName = "quiz")` / `quarkus.langchain4j.ollama-quiz.*`.
- Verifier prompt explicitly states the judged answer needs no self-evaluation, verdicts are parsed as JSON (fence/thinking tolerant), and rejections trigger a single regeneration before a safe fallback.
- Quiz JSON is persisted as raw text so it renders identically after refresh.

## Persistence & Delivery Invariants
- **Anything shown survives refresh:** no transient "uploading"/"analyzing" messages in the transcript; quiz bars are re-derived from `agentType` in history; quiz cards are re-parsed from persisted JSON.
- **Every message carries a timestamp:** persisted history serves `Instant` per message; live SSE payloads and `ChatResponse` carry a server timestamp; the frontend renders a `.message-meta` time under each message (and quiz card) and sorts history chronologically.
- **`INDEXED` means "transcript persisted":** ingestion never flips status; `runAnalysisJob` marks INDEXED only after `route()` returns, so the `INDEXED`-triggered reload always contains the analysis.
- **Thread ownership:** orchestrator rejects sessions owned by another user (`enforceSessionOwnership`); gateway routes async analysis in the active chat thread (fallback `upload:<docId>` only for no-thread callers).
- **Sources:** retrieval filters on `source = "doc:<documentId>"` via metadata equality in the Qdrant/LangChain4j filter, not client-side post-filtering.
- **Proactive follow-ups are durable:** written to the user's last conversation (DB + cache) in addition to the live SSE push.

## Recent Hardening (September 2026)
- Deterministic quiz JSON: named `ollama-quiz` model, `enforceStructuredQuiz` strict-JSON regeneration, lenient verifier JSON parsing, verifier verdict logging.
- Live delivery: SSE auto-reconnect + backoff + dedupe; INDEXED-triggered history backfill; friendly upload transcript + processing chip.
- INDEXED-after-persist ordering so the analysis is always visible on reload.
- Qdrant metadata-equality filter; async jobs on a plain executor inside request context/JTA.
- Proactive follow-ups persisted into the last session; cutoff tuned (`15m`) and overridable.
- Concurrent discussion/summary: doc ingestion stays `PARSED` until the analysis is durably recorded.

## Test & Verification Status
- `orchestrator`: 179 tests / 0 failures / 7 skipped
- `api-gateway`: 66 tests / 0 failures / 11 skipped
- Manual verification required on a running cluster for UI-level behavior (SSE push, quiz cards, refresh persistence).

## Interaction Workflow (updated)
1. **Upload:** user uploads content through Gateway → object storage, immediate `documentId`.
2. **Analyze (async):** orchestrator classifies `CONTENT_ANALYSIS`, embeds chunks, CAA produces a grounded analysis; verifier gates it; transcript persisted → doc `INDEXED` → SSE push.
3. **Assess:** "Generate Quiz" → QGA (`ollama-quiz`, strict JSON) → verifier → quiz cards with per-question answers/explanations and score.
4. **Proactive:** quiet users (cutoff) get a persisted + SSE-pushed follow-up; answering resumes the normal loop.