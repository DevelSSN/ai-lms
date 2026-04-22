from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn
import json
import os
from orchestrator import create_orchestrator
from langchain_core.messages import HumanMessage
from kafka_handler import KafkaHandler
from persistence import RedisPersistence

app = FastAPI(title="AI-LMS Intelligent Core")

# Initialize components
orchestrator_graph = create_orchestrator()
kafka = KafkaHandler(bootstrap_servers=os.getenv("KAFKA_BOOTSTRAP", "localhost:9092"))
redis_store = RedisPersistence(host=os.getenv("REDIS_HOST", "localhost"))

class UserRequest(BaseModel):
    user_id: str
    message: str
    thread_id: str = "default"

def process_kafka_message(value):
    """Processes incoming interactions from Kafka."""
    try:
        data = json.loads(value) if isinstance(value, str) else value
        user_id = data.get('user_id', 'anonymous')
        thread_id = data.get('thread_id', user_id)
        
        # 1. Load state from Redis
        existing_state = redis_store.load_state(thread_id)
        
        # 2. Add new message
        new_messages = existing_state.get('messages', []) + [HumanMessage(content=data.get('message', ''))]
        
        # 3. Run Orchestrator
        inputs = {
            "messages": new_messages,
            "metadata": {"user_id": user_id},
            "user_profile": existing_state.get('user_profile', {})
        }
        result = orchestrator_graph.invoke(inputs)
        
        # 4. Save new state to Redis
        redis_store.save_state(thread_id, result)
        
        # 5. Send response back to Kafka
        last_message = result["messages"][-1].content
        kafka.send_message("processed-responses", {
            "user_id": user_id,
            "response": last_message,
            "status": "completed"
        })
        
    except Exception as e:
        print(f"CRITICAL ERROR in Kafka Consumer: {e}")

@app.on_event("startup")
async def startup_event():
    kafka.start_consumer("user-interactions", process_kafka_message)

@app.get("/health")
async def health_check():
    return {"status": "healthy", "engine": "LangGraph"}

@app.post("/api/v1/orchestrate")
async def orchestrate_sync(request: UserRequest):
    """Synchronous REST endpoint for real-time interactions."""
    try:
        # Load, Run, Save logic (similar to Kafka consumer)
        state = redis_store.load_state(request.thread_id)
        inputs = {
            "messages": state.get('messages', []) + [HumanMessage(content=request.message)],
            "metadata": {"user_id": request.user_id}
        }
        result = orchestrator_graph.invoke(inputs)
        redis_store.save_state(request.thread_id, result)
        
        return {
            "response": result["messages"][-1].content,
            "thread_id": request.thread_id
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
