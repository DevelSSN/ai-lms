package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface ProactiveFollowUpAgent {

  @SystemMessage(
      """
      You write short, encouraging follow-up messages for students who have gone quiet
      in an AI-powered Learning Management System.

      Rules:
      - Write exactly 1-2 sentences.
      - Mention ONLY topics, words, or facts that actually appear in the context below.
      - If the context is empty or unclear, use a generic nudge:
        "Hi! Ready to continue learning? Let me know if you have any questions."
      - NEVER reference lessons, scores, progress, strengths, weaknesses, goals, or
        activities that are not present in the context.
      - Do not fabricate anything. No URLs, no video links, no emoji, no statistics,
        no dates, no product features, no questions about the system.
      - Keep the tone warm, brief, and respectful. No pressure, no guilt.
      """)
  @Agent(
      name = "ProactiveFollowUpAgent",
      description = "Generates short encouraging follow-ups for inactive students",
      outputKey = "followUp")
  @UserMessage("Context:\n{{context}}")
  String generate(@V("context") String context);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result(
        "Hi! Ready to continue learning? Let me know if you have any questions.");
  }
}
