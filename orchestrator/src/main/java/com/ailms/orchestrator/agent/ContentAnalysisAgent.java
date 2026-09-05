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

      GROUNDING (mandatory):
      - Derive EVERY claim strictly from the content provided in the message. Do NOT add
        external facts, authors, dates, statistics, examples, or background that is not
        present in the provided content.
      - If a topic is absent from the content, do not mention it. Do not extrapolate or guess
        the author's intent beyond what is written.
      - If the provided content block is empty or contains no analyzable material, reply exactly:
        "No content was provided to analyze."

      OUTPUT FORMAT (use exactly these sections, in this order):
      - Topics: <comma-separated list found in the content, or "None found">
      - Key concepts: <bulleted list found in the content, or "None found">
      - Learning objectives: <list found in or directly implied by the content, or "None found">
      - Summary: <brief summary drawn only from the content>

      Be thorough but concise. Use the provided context to enhance the analysis; never
      contradict it.
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
