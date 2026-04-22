import redis
import json
from langchain_core.messages import message_to_dict, messages_from_dict

class RedisPersistence:
    def __init__(self, host='localhost', port=6379):
        self.r = redis.Redis(host=host, port=port, decode_responses=True)

    def save_state(self, thread_id: str, state: dict):
        # Convert messages to serializable format
        serializable_state = state.copy()
        if 'messages' in serializable_state:
            serializable_state['messages'] = [message_to_dict(m) for m in serializable_state['messages']]
        
        self.r.set(f"state:{thread_id}", json.dumps(serializable_state))

    def load_state(self, thread_id: str) -> dict:
        data = self.r.get(f"state:{thread_id}")
        if not data:
            return {}
        
        state = json.loads(data)
        if 'messages' in state:
            state['messages'] = messages_from_dict(state['messages'])
        return state
