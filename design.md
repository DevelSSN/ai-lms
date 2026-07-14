# Project Design

## Architecture Flow

```mermaid
flowchart TD

U[User / Client App] -->|Input| API[API Gateway]

API --> ORCH[LLM Orchestrator]

ORCH --> CA[Conversation Agent]
ORCH --> PA[Profiling Agent]
ORCH --> CAA[Context Analysis Agent]
ORCH --> QGA[Question Generation Agent]
ORCH --> IRA[Insight Agent]
ORCH --> PEA[Proactive Agent]

PA --> MEM[(User Profile DB)]
CAA --> VDB[(Vector DB)]
QGA --> VDB
IRA --> MEM

ORCH --> STM[(Cache Memory)]

CAA --> QGA
CAA --> PA
PA --> IRA
VDB --> ORCH
MEM --> ORCH

PEA --> SCHED[Scheduler]
SCHED --> ORCH

CA --> RESP[Response Composer]
RESP --> API
API --> U

API --> OBJ[(Object Storage)]
CAA --> OBJ
```

## Tech Stack
- **Runtime:** Java 25 (GraalVM CE) + Quarkus 3.37.2
- **Build:** Maven 3.9+ multi-module
- **DB:** PostgreSQL 18 (User Profile DB) + pgvector
- **Vector DB:** Qdrant
- **Cache:** Redis 8
- **Messaging:** Apache Kafka 4.3
- **Auth:** Keycloak 26 + OIDC
- **Container:** Docker Compose

## Project Modules

| Module | Artifact | Port | Description |
|--------|----------|------|-------------|
| `common` | `ailms-common` | — | Shared entities, DTOs, constants |
| `api-gateway` | `ailms-api-gateway` | 8080 | REST API, auth, file upload, routing |
| `orchestrator` | `ailms-orchestrator` | 8082 | LLM orchestration + 6 specialized agents |

### System Components

#### 1. API Gateway (`api-gateway/`)
Entry point for all clients. Handles auth (OIDC/Keycloak), request routing, file uploads, and proxies to Orchestrator.

#### 2. LLM Orchestrator (`orchestrator/`)
Central coordinator that:
- Routes tasks to specialized agents via intent classification
- Manages session state via Redis cache
- Aggregates context from Vector DB and User Profile DB

#### 3. Specialized Agents (inside `orchestrator/`)
- **Conversation Agent (CA):** Dialogue flow, conversation logging
- **Profiling Agent (PA):** User preference extraction, knowledge level tracking
- **Content Analysis Agent (CAA):** Document processing, OCR, topic mapping
- **Question Generation Agent (QGA):** Assessment creation from content
- **Insight Agent (IRA):** Progress reports, learning recommendations
- **Proactive Agent (PEA):** Scheduled follow-ups via Scheduler

#### 4. Data Layer
- **User Profile DB (PostgreSQL):** Persistent user data, behavioral patterns
- **Vector DB (Qdrant):** Embeddings for semantic search
- **Object Storage:** Raw user files (PDFs, images, videos)
- **Cache Memory (Redis):** Session context, high-speed storage

#### 5. Supporting Components
- **Scheduler:** Time-based events (reminders, follow-ups) via Quarkus Scheduler
- **Response Composer:** Formats agent output for API Gateway

### Interaction Workflow
1. **Input:** User submits text or content through the API Gateway.
2. **Analysis:** CAA processes files; PA updates the user's context.
3. **Reasoning:** The Orchestrator decides the next step based on the input and current profile.
4. **Generation:** CA or QGA generates a response or assessment.
5. **Output:** Response Composer formats the final output for the user.