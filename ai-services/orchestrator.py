import os
import json
from typing import Annotated, TypedDict, List
from langgraph.graph import StateGraph, END
from langchain_core.messages import BaseMessage, HumanMessage, AIMessage, SystemMessage
from langchain_openai import ChatOpenAI
from qdrant_client import QdrantClient
from qdrant_client.http import models
import psycopg2
import operator
from prompts import PROFILING_SYSTEM_PROMPT, ANALYSIS_SYSTEM_PROMPT, CONVERSATION_SYSTEM_PROMPT

# Configuration
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
POSTGRES_URL = os.getenv("POSTGRES_URL", "dbname=ailms user=user password=password host=localhost")
QDRANT_URL = os.getenv("QDRANT_URL", "http://localhost:6333")

# Clients
llm = ChatOpenAI(model="gpt-4-turbo", api_key=OPENAI_API_KEY)
qdrant = QdrantClient(url=QDRANT_URL)

class AgentState(TypedDict):
    messages: Annotated[List[BaseMessage], operator.add]
    user_profile: dict
    current_task: str
    context: str
    metadata: dict

# --- Refined Agent Nodes ---

def profiling_agent(state: AgentState):
    """Extracts user profile data using LLM."""
    print("--- PROFILING AGENT ---")
    last_msg = state['messages'][-1].content
    
    # LLM-based extraction
    messages = [SystemMessage(content=PROFILING_SYSTEM_PROMPT), HumanMessage(content=last_msg)]
    response = llm.invoke(messages)
    
    try:
        profile_update = json.loads(response.content)
    except:
        profile_update = {"knowledge_level": "unknown"}

    # Persist to Postgres
    try:
        conn = psycopg2.connect(POSTGRES_URL)
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO user_profiles (user_id, knowledge_level) VALUES (%s, %s) ON CONFLICT (user_id) DO UPDATE SET knowledge_level = EXCLUDED.knowledge_level",
            (state['metadata'].get('user_id'), profile_update.get('knowledge_level'))
        )
        conn.commit()
        cur.close()
        conn.close()
    except Exception as e:
        print(f"Profiling Persistence Error: {e}")
        
    return {"user_profile": profile_update}

def content_analysis_agent(state: AgentState):
    """Retrieves and synthesizes context from Qdrant."""
    print("--- CONTENT ANALYSIS AGENT ---")
    last_msg = state['messages'][-1].content
    
    try:
        # Search in Qdrant (assuming 'learning_material' collection exists)
        search_result = qdrant.search(
            collection_name="learning_material",
            query_vector=[0.0] * 1536, # Actual embedding logic would go here
            limit=2
        )
        context_docs = "\n".join([r.payload.get('text', '') for r in search_result])
        
        # Synthesize context
        messages = [SystemMessage(content=ANALYSIS_SYSTEM_PROMPT), HumanMessage(content=f"Context: {context_docs}\nQuery: {last_msg}")]
        response = llm.invoke(messages)
        context = response.content
    except Exception:
        context = "General educational knowledge applied."
        
    return {"context": context}

def conversation_agent(state: AgentState):
    """Final response generation."""
    print("--- CONVERSATION AGENT ---")
    
    full_prompt = CONVERSATION_SYSTEM_PROMPT.format(
        context=state.get('context', 'None'),
        profile=json.dumps(state.get('user_profile', {}))
    )
    
    messages = [SystemMessage(content=full_prompt)] + state['messages']
    response = llm.invoke(messages)
    
    return {"messages": [response]}

# --- Graph ---

def create_orchestrator():
    workflow = StateGraph(AgentState)
    workflow.add_node("profiling", profiling_agent)
    workflow.add_node("analysis", content_analysis_agent)
    workflow.add_node("conversation", conversation_agent)

    workflow.set_entry_point("profiling")
    workflow.add_edge("profiling", "analysis")
    workflow.add_edge("analysis", "conversation")
    workflow.add_edge("conversation", END)

    return workflow.compile()
