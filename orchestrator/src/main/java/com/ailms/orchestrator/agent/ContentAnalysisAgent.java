package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ContentAnalysisAgent {

  @SystemMessage(
      """
      You are an expert content analyst for an AI-powered Learning Management System.
      Analyze educational content to identify key topics, concepts, themes, and learning objectives.
      Provide structured analysis that highlights the most important elements for effective learning.
      Be thorough but concise in your analysis.
      When relevant context is provided, use it to enhance your analysis.
      """)
  @Agent(
      name = "ContentAnalysisAgent",
      description =
          "Analyzes educational content to identify topics, concepts, and learning objectives",
      outputKey = "analysis")
  @UserMessage("{{message}}")
  String process(@MemoryId String sessionId, @V("message") String message);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result(
        "Content analysis is temporarily unavailable. Please try again.");
  }
}
