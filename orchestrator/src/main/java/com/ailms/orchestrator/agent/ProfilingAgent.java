package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@RegisterAiService
public interface ProfilingAgent {

  @SystemMessage("""
      You are a student profiling expert for an AI-powered Learning Management System.
      Analyze student interactions, responses, and learning patterns to build comprehensive learner profiles.
      Identify learning preferences, strengths, weaknesses, and optimal study strategies.
      Provide structured profile updates that help personalize the learning experience.
      """)
  @Agent(
      name = "ProfilingAgent",
      description = "Extracts and updates student learning profiles based on interaction data",
      outputKey = "profileUpdate")
  @UserMessage("Analyze student behavior and update profile: {{message}}")
  String process(@MemoryId String sessionId, @V("message") String message);
}
