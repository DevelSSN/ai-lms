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
from prompts import (
    PROFILING_SYSTEM_PROMPT, 
    ANALYSIS_SYSTEM_PROMPT, 
    CONVERSATION_SYSTEM_PROMPT,
    QGA_SYSTEM_PROMPT,
    IRA_SYSTEM_PROMPT,
    PEA_SYSTEM_PROMPT
)

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

def question_generation_agent(state: AgentState):
    """Generates assessment items."""
    print("--- QUESTION GENERATION AGENT ---")
    
    messages = [
        SystemMessage(content=QGA_SYSTEM_PROMPT), 
        HumanMessage(content=f"Content: {state.get('context', 'No content available')}\nUser Level: {state.get('user_profile', {}).get('knowledge_level', 'unknown')}")
    ]
    response = llm.invoke(messages)
    
    return {"messages": [response]}

def insight_agent(state: AgentState):
    """Provides learning insights and recommendations."""
    print("--- INSIGHT AGENT ---")
    
    messages = [
        SystemMessage(content=IRA_SYSTEM_PROMPT),
        HumanMessage(content=f"User Profile: {json.dumps(state.get('user_profile', {}))}")
    ]
    response = llm.invoke(messages)
    
    return {"messages": [response]}

def proactive_agent(state: AgentState):
    """Triggers proactive interactions."""
    print("--- PROACTIVE AGENT ---")
    
    messages = [
        SystemMessage(content=PEA_SYSTEM_PROMPT),
        HumanMessage(content=f"Context: {state.get('context', 'None')}\nLast message: {state['messages'][-1].content}")
    ]
    response = llm.invoke(messages)
    
    # In a real system, this might trigger a scheduler
    return {"messages": [response]}

# --- Graph ---

def create_orchestrator():
    workflow = StateGraph(AgentState)
    
    # Add Nodes
    workflow.add_node("profiling", profiling_agent)
    workflow.add_node("analysis", content_analysis_agent)
    workflow.add_node("conversation", conversation_agent)
    workflow.add_node("question_gen", question_generation_agent)
    workflow.add_node("insights", insight_agent)
    workflow.add_node("proactive", proactive_agent)

    # Entry point
    workflow.set_entry_point("profiling")
    
    # Edges
    workflow.add_edge("profiling", "analysis")
    
    # Simple routing based on current_task if provided, else default to conversation
    def route_tasks(state: AgentState):
        task = state.get("current_task", "conversation")
        if task == "quiz":
            return "question_gen"
        elif task == "insights":
            return "insights"
        elif task == "proactive":
            return "proactive"
        else:
            return "conversation"

    workflow.add_conditional_edges(
        "analysis",
        route_tasks,
        {
            "question_gen": "question_gen",
            "insights": "insights",
            "proactive": "proactive",
            "conversation": "conversation"
        }
    )

    workflow.add_edge("conversation", END)
    workflow.add_edge("question_gen", END)
    workflow.add_edge("insights", END)
    workflow.add_edge("proactive", END)

    return workflow.compile()
