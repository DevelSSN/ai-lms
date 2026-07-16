package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@RegisterAiService
public interface ConversationAgent {

  @SystemMessage("""
      You are a friendly and knowledgeable AI tutor for an AI-powered Learning Management System.
      Help students with their questions, provide clear and educational explanations,
      and guide them through their learning journey. Be concise but thorough.
      Always respond in a helpful and encouraging tone.
      """)
  @Agent(
      name = "ConversationAgent",
      description = "Handles general conversation and tutoring with students",
      outputKey = "response")
  @UserMessage("{{message}}")
  String process(@MemoryId String sessionId, @V("message") String message);
}
