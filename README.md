# AI-LMS: Event-Driven Microservices

AI-powered Learning Management System. Quarkus 3.37.2 + Java 25.

## Project Structure

- `api-gateway/`: Quarkus REST API — auth, chat, content upload, profile
- `orchestrator/`: Quarkus service — LLM orchestration + specialized agents
- `common/`: Shared entities, DTOs, constants
- `infra/`: Podman Compose for PostgreSQL, Redis, Kafka, Qdrant, Keycloak
- `frontend/`: HTML/CSS/JS chat interface

## Architecture

```
User → API Gateway → Orchestrator → Agents (Conversation, Profiling, Content Analysis, Question Generation, Insight, Proactive)
         ↓                ↓
    Object Storage    DBs (PostgreSQL, Redis, Qdrant)
```

## Prerequisites

- Java 25 (GraalVM CE)
- Maven 3.9+
- Podman 5+

## Getting Started

### 1. Start Infrastructure
```bash
cd infra
cp ../.env.example .env   # first time only, edit passwords
podman compose up -d
```

### 2. Build All Modules
```bash
mvn clean install
```

### 3. Run API Gateway
```bash
cd api-gateway
mvn quarkus:dev
```

### 4. Run Orchestrator
```bash
cd orchestrator
mvn quarkus:dev
```

### 5. Open Frontend
Open `frontend/index.html` in browser.

## Ports

| Service | Port |
|---------|------|
| API Gateway | http://localhost:10080 |
| Orchestrator | http://localhost:10082 |
| Keycloak | http://localhost:10081 |
| Kafka | localhost:11092 |
| Qdrant | http://localhost:10633 |
