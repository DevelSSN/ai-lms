# AI-LMS Orchestrator Integration Plan

> Working branch: `shashank/hardening`
> One commit per change. Full test run against a green baseline before starting:
> 182 tests pass, 21 `@Disabled`.

## Completed Phases (historical, checked)

- [x] Phase 1: Dependencies & Config (LangChain4j, Qdrant, Redis, Kafka)
- [x] Phase 2: Chat History & Memory (Redis + PostgreSQL)
- [x] Phase 3: Agent Interfaces (6 classes converted to AI Service interfaces)
- [x] Phase 4: Agentic Orchestration (IntentClassifier, Router, Pipeline)
- [x] Phase 5: Response Composer
- [x] Phase 6: Vector DB Integration (Qdrant)
- [x] Phase 7: Kafka Integration
- [x] Phase 8: Error Handling & Edge Cases
- [x] Phase 16: Frontend Upload UI

---

## Phase A — Foundation Fixes (bugs in "completed" phases)

Fixes land first because they unblock the DB, tests, and downstream data flow.

- [ ] A1. `ChatHistoryCacheService` / `ChatHistory.ChatMessage` — replace `||`-delimited Redis cache with JSON serialization; add `timestamp` to `ChatMessage` (`fixes 2.1, 22.6`)
- [ ] A2. `ContentEmbedding.embedding` — Hibernate pgvector `vector(768)` type mapping; add `quarkus-jdbc-postgresql` to `common`; H2-safe test schema (`fixes 6.2, 10.3, 22.5`)
- [ ] A3. `QdrantInitializer` — align `qdrant.rest.*` keys with `quarkus.langchain4j.qdrant.*`; single port source of truth (10634) (`fixes 23.5`)
- [ ] A4. Profiling persistence — write `ProfilingAgent` output into `UserProfile`; call `publishProfileUpdated` (`fixes 3.1, 3.2, 26.3`)
- [ ] A5. Routing — remove `youtube`/`DOC_REFERENCE` keyword short-circuits; LLM classifier decides except explicit links (`fixes 4, 25.3`)
- [ ] A6. `ResponseVerifierAgent` — JSON verdict parse (not `contains`); retry loop; fail-closed on LLM exception (`fixes 4`)
- [ ] A7. `InsightAgent` + `ResponseComposer` — feed real analytics; write `analysis`/`assessment`/`insights` scope keys (`fixes 4, 5`)
- [ ] A8. Agent chat memory — distinct `@MemoryId` namespaces per agent (`conversation:`/`analysis:`/`assessment:`/`insight:`/`profiling:`)
- [ ] A9. `ObjectStorageService.readFile` — throw on infra error instead of `null` (`fixes 8.2`)
- [ ] A10. `YouTubeLinkValidator` fail-closed; `@Blocking` on YouTube HTTP calls (`fixes 8.1, 25.2, 25.4`)
- [ ] A11. `ProactiveAgent` — `markProactiveSent` on Kafka ack; remove dead `ProactiveFollowUp` record (`fixes 7.3, 26.4`)

## Phase B — Security (critical)

- [ ] B1. `SseBroadcastService` — per-user emitters; Jackson JSON (escape control chars); bounded registry + backpressure; keepalive NPE fix (`fixes 17.1, 17.3, 17.4, 17.5, 17.6`)
- [ ] B2. `/api/updates` — OIDC auth (remove `@PermitAll`); scoped `TokenFromQueryFilter` fail-closed (`fixes 17.2`)
- [ ] B3. IDOR / thread ownership — gateway verifies `sessionId` belongs to JWT subject; scoped listing; orchestrator server-side validation (`fixes 18.1–18.4`)
- [ ] B4. Upload — file-size limit; transactional S3→DB with orphan cleanup; filename sanitize (`fixes 9 DoS, audit`)
- [ ] B5. Exposure — strip exception messages from responses; harden `/tmp` log; gate Swagger UI; don't commit default secrets (`13, 23.4`)

## Phase C — Data Plumbing (incomplete phases 9–12, 15, 26)

- [ ] C1/9. Object storage full flow — `ContentResource.uploadFile` → S3 + `ContentDocument`; `ContentAnalysisAgent` reads stored content (not just filename); wire `/assess` & `/insights`
- [ ] C2/10. pgvector dual-write idempotency (`CREATE EXTENSION vector`, Postgres vector column alongside Qdrant)
- [ ] C3/11. CAA→QGA data flow — analysis context to `QuestionGenerationAgent`; wire `questionCount`/`difficulty` to orchestrator; `AssessmentItem.explanation`/`sessionId`
- [ ] C4/12. OCR / doc processing — reuse `DocumentParserService` bean; wire into CAA pipeline; unsupported-type errors
- [ ] C5/15. Kafka expansion — implement `content-analysis-complete`, `profile-updated`, `insight-generated` handlers
- [ ] C6/26. Proactive delivery — SSE-push follow-ups to owning user (SSE only; email deferred)

## Phase D — Cross-cutting Quality (13, 19–25)

- [ ] D1/13. RBAC — `@RolesAllowed` on gateway endpoints (student/teacher/admin); `@PermitAll`/`@DenyAll`; enable `AuthEnforcementTest`
- [ ] D2/19. Input validation (`@Size`/`@NotBlank`/`@Min`/`@Max`) + uniform `ExceptionMapper`s (400/404/409/502)
- [ ] D3/20. Tests — un-disable repo/integration/Redis/Qdrant/SSE tests; fix gateway test port collision (10081)
- [ ] D4/21. Observability — micrometer metrics, OpenTelemetry, structured logging, liveness/readiness probes
- [ ] D5/22. DB schema — indexes, `@Version` optimistic locking, soft-delete filters, dead-field cleanup
- [ ] D6/23. Containers/config — Dockerfile MODULE arg fix, pin versions, `USER 1001`, MinIO creds to services, `.env` reconcile
- [ ] D7/24. Constants & enums — `common` `enums` package; replace magic strings
- [ ] D8/25. YouTube hardening — config-driven API-key flag, regex edge cases

## Phase E — Critical Bug Fixes (blocks demo)

Fixes that must land before any new features. Derived from deep code audit (2026-09-10).

- [x] E1. Greeting responses skip verifier — `OrchestratorService.java:196-198`: wrap `verifyAndRetry()` in `if (greetingResponse == null)`. Saves ~500ms + wasted LLM call per greeting.
- [x] E2. Rejected responses not shipped — `OrchestratorService.java:507-509,515-516`: when retry returns blank or double-rejection occurs, return user-facing fallback ("I couldn't generate a good answer. Could you rephrase?") instead of the rejected original.
- [x] E3. Profiling agent receives polluted input — `OrchestratorService.java:207`: change `enrichedMessage` → `message` so ProfilingAgent only sees what the student typed, not vector DB document chunks.
- [x] E4. CAA→QGA data flow — `OrchestratorService.resolveAnalysisContext()` (lines 396-405): retrieve CAA's structured analysis from `ChatMemoryKeys.analysis(sessionId)` chat memory and prepend to `analysisCtx` before passing to QGA. This is the critical link for document-to-assessment.
- [x] E5. VectorDBService non-atomic dual-write — `VectorDBService.java:57-83`: reorder ingest to add all Qdrant vectors first, then delete old ones. Crash leaves stale-but-complete data instead of partial.
- [ ] E6. Qdrant config key mismatch — `QdrantInitializer.java:21-29`: introduce `qdrant.admin.host` config key instead of borrowing from `quarkus.langchain4j.qdrant.host` (gRPC target). Decouple admin REST from gRPC connection.
- [ ] E7. Source-filtered retrieval undershooting — `VectorDBService.java:102-123`: push source filter into `EmbeddingSearchRequest` metadata filter instead of client-side filtering. Ensures `maxResults` is actually returned.

### E8. SSE & ProactiveAgent delivery fixes

The proactive follow-up pipeline works end-to-end under ideal conditions but is fragile in practice. These fixes make delivery reliable.

- [ ] E8a. SseBroadcastService registry leak — `SseBroadcastService.java:37-45`: `computeIfAbsent` inserts an empty list before the `MAX_USERS` guard. On rejection, the orphaned entry is never removed. Fix: check `userEmitters.size()` before `computeIfAbsent`, or `userEmitters.remove(userId, list)` in the rejection path. Also fix off-by-one: line 43 uses `>` instead of `>=`.
- [ ] E8b. SseBroadcastService race on fresh emitters — `SseBroadcastService.java:97`: `requested() <= 0` evicts emitters before the SSE sink registers demand. A Kafka event arriving in the window between emitter creation and first `request(n)` silently drops the message. Fix: only check `emitter.isCancelled()`, let Mutiny buffer zero-demand emissions.
- [ ] E8c. ProactiveAgent long-running transaction — `ProactiveAgent.java:40-62`: entire `checkFollowUps()` runs in a single `@Transactional`. For N inactive users, each LLM call (seconds) holds the DB connection. Fix: remove `@Transactional` from the method; `markProactiveSentIfNotRecent` already has its own. Extract LLM call + Kafka send into a non-transactional method.
- [ ] E8d. ProactiveAgent fire-and-forget Kafka send — `ProactiveAgent.java:56`: `eventEmitter.send()` returns `CompletableFuture<Boolean>` which is discarded. If Kafka is down, the user's `lastProactiveSentAt` is already updated but the message was never sent. Fix: `.send(event).exceptionally(ex -> { log.error("Proactive Kafka send failed", ex); return false; })`. On failure, revert `lastProactiveSentAt` so the user is retried next cycle.
- [ ] E8e. ProactiveAgent double `Instant.now()` — `ProactiveAgent.java:44,51`: cutoff is computed once at line 44, but line 51 computes two new `Instant.now()` values inside the loop. Over a long-running loop the cutoff shifts. Fix: capture `Instant now = Instant.now()` once before the loop and reuse.
- [ ] E8f. ConversationRepository includes deleted logs — `ConversationRepository.findInactiveUsersSince()` (lines 189-194) and `findRecentByUserId()` (lines 197-199): no `deleted` filter. Users who deleted all conversations appear inactive and receive unsolicited follow-ups with deleted content. Fix: add `WHERE (deleted IS NULL OR deleted = false)` to both queries.
- [ ] E8g. SseEventBridge null userId guard — `SseEventBridge.java:18-38`: no null check on `event.userId()` before `sse.broadcast()`. Malformed Kafka messages could trigger NPE or broadcast to `null` key. Fix: add `if (event.userId() == null) { log.warn("Skipping event with null userId"); return; }` to each handler.
- [ ] E8h. TokenFromQueryFilter format validation — `TokenFromQueryFilter.java:25-26`: any arbitrary string from `?token=` is injected into the `Authorization` header without validation. Fix: reject tokens that don't match `[A-Za-z0-9._\-]+` (JWT characters only).
- [ ] E8i. InteractResource blocks event loop — `InteractResource.java:46-47`: synchronous `orchestrator.processMessage()` REST call blocks the Quarkus event-loop thread, starving SSE connections. Fix: make the method `@Blocking` or return `Uni<ChatResponse>`.
- [ ] E8j. Orchestrator KafkaEventSubscriber stubs — `KafkaEventSubscriber.java:13-39`: all 4 `@Incoming` handlers (`handleProactiveEvent`, `handleContentAnalysisComplete`, `handleProfileUpdated`, `handleInsightGenerated`) are log-only no-ops. The orchestrator subscribes to its own outbound topics and wastes resources polling. Fix: either (a) remove the `-in` channel configs and delete the subscriber class entirely (the gateway already relays everything via SseEventBridge), or (b) implement actual chaining logic (e.g. profile-updated → trigger insight generation, content-analysis-complete → trigger profiling). Option (a) is simpler and correct for current architecture.
- [ ] E8k. No Kafka dead-letter topic — failed messages (deserialization errors, broadcast exceptions) are silently lost with no retry or dead-letter queue. If SseBroadcastService throws or a malformed event arrives, the message is gone. Fix: add `mp.messaging.incoming.*.dead-letter-enable=true` and `mp.messaging.incoming.*.dead-letter-topic=dlq-{channel}` config for all 4 incoming channels in both api-gateway and orchestrator application.properties. Add a DLQ consumer that logs failed messages for manual inspection.
- [ ] E8l. No Kafka consumer group IDs — none of the 4 `mp.messaging.incoming.*` channels in api-gateway specify `group.id`. Auto-generated groups mean multiple gateway instances each receive all messages (fan-out), causing duplicate SSE pushes. Fix: set `mp.messaging.incoming.proactive-events.group.id=ailms-gateway-sse` (and same pattern for the other 3 channels) so instances share a consumer group.

## Phase F — Document-to-Assessment Pipeline (hero demo)

Upload a textbook chapter → get a personalized, grounded quiz. Depends on Phase E.

- [ ] F1. Wire CAA analysis into QGA context — after E4, verify that "Generate quiz about this document" produces questions grounded in the CAA's topic/concept extraction, not just raw chunks.
- [ ] F2. Add difficulty parameter to QGA — `QuestionGenerationAgent.java` prompt: add `{{difficulty}}` template variable (easy/medium/hard). `OrchestratorService`: parse difficulty from user message or default to medium. (Supersedes C3 dead fields.)
- [ ] F3. Add question count parameter to QGA — allow "Generate 10 questions" or "Generate 3 questions". Parse from user message, default to 5. (Supersedes C3 dead fields.)
- [ ] F4. Add `AssessmentItem.explanation` and `sessionId` fields — complete the QGA output schema so quiz answers include explanations and can be tracked per session. (Supersedes C3.)
- [ ] F5. Frontend "Generate Quiz" quick action — `app.js`: after file upload + CAA analysis is displayed, show a "Generate Quiz" button that sends "Generate 5 quiz questions about this document".
- [ ] F6. Quiz display UI — `app.js` + `style.css`: numbered questions with radio-button options, click to reveal answer + explanation, score tracking (correct/total). Lightweight, no framework.
- [ ] F7. Quiz result persistence — new entity `QuizResult` (userId, sessionId, docId, questions, answers, score, timestamp). Store in PostgreSQL. Feed results back into ProfilingAgent for weak-area tracking.

## Phase G — Verifiable Sources & Citations

"This answer came from page X of your document." Depends on Phase E.

- [ ] G1. Return chunk metadata from retrieval — `VectorDBService.retrieveRelevantContext()`: change return type from `List<String>` to `List<RetrievedChunk>` record (text, source, score, chunkIndex). Propagate through `retrieveScopedContext()`.
- [ ] G2. Pass chunk references to ConversationAgent — `OrchestratorService`: store chunk metadata in request-scoped context alongside enriched message. Update ConversationAgent system prompt to instruct citation by chunk number when answering from RAG context.
- [ ] G3. Format citations in ResponseComposer — `ResponseComposer`: when answer contains citation markers (`[1]`, `[source: N]`), map to actual document name + chunk text. Return citation metadata alongside response.
- [ ] G4. Display citations in frontend — `app.js`: parse citation markers in bot responses, render as expandable footnotes ("From: {filename}, chunk {N}"). Small CSS addition for footnote styling.

## Phase H — Privacy-First Features (RBAC + Analytics)

Role-based access and learning analytics dashboard. Depends on Phase B (SSE security).

- [ ] H1. Add Keycloak roles — realm export: add `STUDENT`, `TEACHER`, `ADMIN` roles to `ailms` realm. Update `keycloak/realm-export.json`.
- [ ] H2. RBAC on gateway endpoints — `ChatResource`, `ContentResource`, `ProfileResource`: add `@RolesAllowed` annotations. Students: own data only (enforce `userId == JWT subject`). Teachers: any student's profile/insights. Admin: full access.
- [ ] H3. Teacher-student association — new entity `TeacherStudent` (teacherId, studentId) or use Keycloak group membership. Add repository + resource for managing associations.
- [ ] H4. Student analytics endpoint — `GET /api/v1/analytics/student/{studentId}`: topics studied (from profile), conversation count, documents analyzed, quiz scores, last active. SQL queries on existing tables.
- [ ] H5. Class analytics endpoint — `GET /api/v1/analytics/class`: aggregated stats (avg score, topic coverage heatmap, active student count). Teacher role required.
- [ ] H6. Analytics dashboard frontend — `app.js` + `style.css`: teacher view with student selector, knowledge coverage bar chart, activity timeline, quiz score history. Vanilla JS + Chart.js (CDN, no build step).

## Deferred (by decision)

- **Phase 14 (GraalVM native build)** — not run in this session; only Dockerfile correctness fixes kept under D6.
- **Phase 26 email delivery** — SSE push only; `quarkus-mailer` not added.
- **Phase I (Offline deployment packaging)** — single `deploy.sh` script + model pre-download + health checks. Low priority, defer until core features work.

---

## Known Bugs / Incomplete Items in "Completed" Phases (deep code audit — 2026-09-05)

### Phase 2 — Chat History & Memory
- [ ] `ChatHistoryCacheService.java:31` — Redis delimiter `||` corrupts cache reads when message content contains `||` (`split("\\|\\|", 3)` truncates)
- [ ] `ChatHistory.ChatMessage` drops `timestamp` — cannot render message times client-side

### Phase 3 — Profiling Agent output never persists
- [ ] `ProfilingAgent.process()` result is fire-and-forget — never written to `UserProfile`; `ProfilingService` only creates empty rows
- [ ] `KafkaEventPublisher.publishProfileUpdated()` exists but is **never called**

### Phase 4 — Orchestration short-circuits & insufficient data
- [ ] `OrchestratorService.java:75,141` — any message containing "youtube" force-routes to `VIDEO_SEARCH`, bypassing LLM classifier (e.g. "Explain what YouTube is")
- [ ] `OrchestratorService.java:148` — `DOC_REFERENCE` regex matches normal phrases ("Can I get a PDF of the syllabus")
- [ ] `InsightAgent` is fed the raw user message, never real analytics — always answers "Not enough data"
- [ ] `ResponseVerifierAgent` — fragile `contains("NEEDS_REWRITE")` string match + fail-open on LLM exceptions + rejected response accepted after single retry

### Phase 5 — Response Composer fallback keys
- [ ] `ResponseComposer.java` — fallback keys `analysis`/`assessment`/`insights` may never be populated (depends on unverified `AgenticScope` wiring)

### Phase 6 — Vector DB
- [ ] `QdrantInitializer.java:21-37` — reads `qdrant.rest.*` keys not present in `application.properties`; port 10633 vs configured 10634 mismatch → collection init silently fails
- [ ] `ContentEmbedding.embedding` — `vector(768)` column on `float[]` with **no Hibernate pgvector type mapping** → first real persist fails; also breaks H2 test schema
- [ ] `VectorDBService.ingestDocumentChunks` — no idempotency; partial Qdrant failure leaves counts inconsistent with PostgreSQL

### Phase 7 — Kafka
- [ ] `KafkaEventSubscriber` — `handleContentAnalysisComplete`, `handleProfileUpdated`, `handleInsightGenerated` are no-op stubs
- [ ] Proactive follow-ups generated but **never delivered to user** — only logged
- [ ] `ProactiveAgent.java:52-53` — `markProactiveSent` races the async event emitter; user may never be re-pinged if delivery fails

### Phase 8 — Error handling gaps
- [ ] `YouTubeLinkValidator` fail-open — oEmbed network error → invalid URL accepted
- [ ] `ObjectStorageService.readFile` returns `null` for both not-found and infra errors — masks outages

---

## Audit Status of Unchecked Phases (deep code audit — 2026-09-05)

| Phase | Status |
|------|--------|
| 9. Object Storage & File Persistence | **Partially done, broken:** MinIO in compose but S3 creds never passed to app services; upload non-transactional (S3→DB, orphaned blobs on failure); **no file-size limit** (DoS); `/assess` & `/insights` are stub prompts — no real extraction/embeddings |
| 10. pgvector | **Dangerously half-implemented:** `vector(768)` column declared in `ContentEmbedding` but no type mapping (see Phase 6 audit) |
| 11. CAA → QGA | Not done: `AssessmentRequest.questionCount`/`difficulty` are dead fields, never reach orchestrator; `AssessmentItem` missing `explanation` & `sessionId` |
| 12. OCR & Doc Processing | Implemented but not wired to audit: `AutoDetectParser` allocated per call; unsafe UTF-8 binary fallback for unsupported types |
| 13. Auth RAMC | Not done. `AuthEnforcementTest` fully `@Disabled`; realm-export `redirectUris: ["*"]`; default secrets committed |
| 14. GraalVM Native | **Buggy:** `Dockerfile.native` ignores `MODULE` build arg → both `orchestrator-native` AND `api-gateway-native` run the orchestrator binary; `graalvm-ce:latest` unpinned; no `USER 1001`. **Deferred.** |
| 15. Kafka Expansion | Not done — channels exist but subscribers are no-ops |
| 17. SSE Security | Not done — global broadcast, `@PermitAll`, token-in-query, no overflow control, keepalive NPE |
| 18. IDOR / Thread Ownership | Not done — no ownership verification anywhere |
| 19. Input Validation & Error Mapping | Not done — bare 500s, unvalidated DTOs |
| 20. Test Enablement | Not done — 21 tests `@Disabled`, gateway test port 10081 collides with Keycloak |
| 21. Observability | Not done |
| 22. DB Schema Hardening | Not done |
| 23. Container & Config Consolidation | Not done |
| 24. Constants & Enum Centralization | Not done |
| 25. YouTube Search Hardening | Not done |
| 26. Proactive Notification Delivery | Not done — never delivered to user |