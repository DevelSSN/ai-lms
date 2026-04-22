# AI-LMS: Event-Driven Microservices

This project is an AI-powered Learning Management System using a modern event-driven architecture.

## Project Structure

- `api-gateway/`: Java Quarkus service handling authentication and request routing.
- `ai-services/`: Python FastAPI services for LLM orchestration and specialized agents.
- `infra/`: Docker Compose for PostgreSQL, Redis, Qdrant, Redpanda, and Keycloak.
- `frontend/`: A premium, minimalistic chat interface with video embedding support.

## Getting Started

### 1. Spin up Infrastructure
```bash
cd infra
docker-compose up -d
```

### 2. Run Python AI Services
```bash
cd ai-services
pip install -r requirements.txt
python main.py
```

### 3. Run Quarkus API Gateway
```bash
cd api-gateway
mvn quarkus:dev
```

### 4. Open Frontend
Simply open `frontend/index.html` in your browser.

## Architecture Details
See [proposed_architecture.md](file:///home/archdev/.gemini/antigravity/brain/1b85b8fe-b2dd-41fe-bf46-9bf1b061c78b/proposed_architecture.md) for the full design.
