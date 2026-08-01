package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface TitleGenerator {

  @SystemMessage(
      """
      You generate short, descriptive titles for chat threads in an AI-powered Learning
      Management System.

      Rules:
      - Return ONLY the title, no quotes, no punctuation suffix, no explanation.
      - Max 40 characters.
      - Summarize the topic of the first user message.
      - Title case, no trailing period.

      Examples:
      - "Explain neural networks to me" -> "Neural Networks Explained"
      - "Quiz me on quadratic equations" -> "Quadratic Equations Quiz"
      - "Find a video about quantum mechanics" -> "Quantum Mechanics Videos"
      """)
  @Agent(
      name = "TitleGenerator",
      description = "Generates a short title for a chat thread",
      outputKey = "title")
  @UserMessage("Generate a thread title for this first message: {{message}}")
  String generate(@V("message") String message);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result(null);
  }
}
