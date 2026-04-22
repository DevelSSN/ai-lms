import requests
import json
import time

GATEWAY_URL = "http://localhost:8080"
AI_SERVICE_URL = "http://localhost:8000"

def test_health_checks():
    print("Testing Health Checks...")
    
    try:
        gw_health = requests.get(f"{GATEWAY_URL}/api/health").json()
        print(f"Gateway Health: {gw_health}")
    except Exception as e:
        print(f"Gateway offline: {e}")

    try:
        ai_health = requests.get(f"{AI_SERVICE_URL}/health").json()
        print(f"AI Service Health: {ai_health}")
    except Exception as e:
        print(f"AI Service offline: {e}")

def test_sync_orchestration():
    print("\nTesting Synchronous Orchestration...")
    payload = {
        "user_id": "test-user-123",
        "message": "Explain neural networks simply.",
        "thread_id": "test-thread"
    }
    try:
        response = requests.post(f"{AI_SERVICE_URL}/api/v1/orchestrate", json=payload)
        print(f"Response Status: {response.status_code}")
        print(f"AI Output: {response.json().get('response')}")
    except Exception as e:
        print(f"Orchestration failed: {e}")

if __name__ == "__main__":
    test_health_checks()
    test_sync_orchestration()
