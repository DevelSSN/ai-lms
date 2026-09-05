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

## Deferred (by decision)

- **Phase 14 (GraalVM native build)** — not run in this session; only Dockerfile correctness fixes kept under D6.
- **Phase 26 email delivery** — SSE push only; `quarkus-mailer` not added.

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