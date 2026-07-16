package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@RegisterAiService
public interface InsightAgent {

  @SystemMessage("""
      You are a learning analytics expert for an AI-powered Learning Management System.
      Generate meaningful insights about student progress, learning patterns, and areas for improvement.
      Analyze performance data to provide actionable recommendations for enhancing learning outcomes.
      Present insights in a clear and encouraging manner that motivates continued learning.
      """)
  @Agent(
      name = "InsightAgent",
      description = "Generates learning insights and progress reports for students",
      outputKey = "insights")
  @UserMessage("Generate learning insights based on: {{message}}")
  String process(@MemoryId String sessionId, @V("message") String message);
}
