# System Validation Guide: AI-LMS

This document outlines the validation checkpoints to ensure the entire microservices stack is integrated and functioning correctly.

## 1. Component Health Check

| Component | Endpoint / Port | Verification Command |
| :--- | :--- | :--- |
| **API Gateway** | `localhost:8080` | `curl http://localhost:8080/api/health` |
| **AI Orchestrator** | `localhost:8000` | `curl http://localhost:8000/health` |
| **Keycloak (Auth)** | `localhost:8180` | Open `http://localhost:8180` in browser |
| **Redpanda (Kafka)**| `localhost:9092` | `rpk cluster info` (if rpk installed) |
| **PostgreSQL** | `localhost:5432` | `psql -h localhost -U user -d ailms` |
| **Qdrant** | `localhost:6333` | `curl http://localhost:6333/health` |

## 2. Integration Checkpoints

### A. Authentication Flow
- **Target**: Ensure `quarkus-oidc` rejects unauthorized requests.
- **Test**: `curl -X POST http://localhost:8080/api/interact -d '{"message": "test"}'`
- **Expected**: `401 Unauthorized` (until a valid JWT is provided).

### B. Async Messaging Flow (Kafka)
- **Trace**: 
    1. Gateway receives request.
    2. Gateway emits to `user-interactions`.
    3. Python Service consumes from `user-interactions`.
    4. Python Service emits to `processed-responses`.
    5. Gateway logs the processed response.
- **Verification**: Check logs of `api-gateway` and `ai-services` for "Interaction queued" and "Processing Kafka message".

### C. Stateful Orchestration (Redis)
- **Target**: Verify conversation history is persisted.
- **Test**: Send two messages to `/api/v1/orchestrate` with the same `thread_id`.
- **Expected**: The AI response should acknowledge the previous message context.

### D. RAG Knowledge (Qdrant)
- **Target**: Verify the AI uses the custom knowledge base.
- **Action**: Run `python infra/setup_qdrant.py` first.
- **Test**: Ask about "Neural networks".
- **Expected**: The response should include details about "Backpropagation" or "biological modeling" from the sample data.

### E. User Profiling (Postgres)
- **Target**: Verify traits are extracted and stored.
- **Test**: Interact as a "Beginner".
- **Verification**: `SELECT * FROM user_profiles;` in Postgres.
- **Expected**: The `knowledge_level` column should be updated to `beginner` or `intermediate`.

## 3. Full System Launch Sequence

1. **Infrastructure**:
   ```bash
   cd infra
   docker-compose up -d
   ```
2. **Setup Data**:
   ```bash
   python infra/setup_qdrant.py
   ```
3. **Launch AI Core**:
   ```bash
   cd ai-services
   python main.py
   ```
4. **Launch Gateway**:
   ```bash
   cd api-gateway
   mvn quarkus:dev
   ```

## 4. Troubleshooting
- **Kafka Connection**: Ensure `localhost:9092` is accessible from both Quarkus and Python. If running inside Docker for both, use the service name `redpanda` instead of `localhost`.
- **OpenAI Key**: Ensure `OPENAI_API_KEY` is exported in the environment where `ai-services` is running.
