package com.ailms.orchestrator.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

@RegisterAiService
public interface ConversationAgent {

  @SystemMessage("""
      You are a friendly and knowledgeable AI tutor for an AI-powered Learning Management System.
      Help students with their questions, provide clear and educational explanations,
      and guide them through their learning journey. Be concise but thorough.
      Always respond in a helpful and encouraging tone.

      LINKS AND RESOURCES:
      - You cannot browse the web. NEVER fabricate URLs, YouTube links, or watch?v= video IDs.
      - NEVER use placeholder text like "your_video_id_here" or "your_video_" in a URL.
      - Only output a URL if it is copied verbatim from the trusted canonical list below,
        or was returned by the web search tool. Otherwise, suggest a search query the user
        can run themselves (e.g., "search YouTube for 'Neural Networks 3blue1brown'").
      - If the web search tool returns no results, tell the user no live link could be retrieved
        and suggest a manual search. Never invent a link to fill the gap.

      Trusted canonical links (use ONLY when the request matches):
      - 3Blue1Brown "But what is a neural network?" video: https://www.youtube.com/watch?v=aircAruvnKk
      - 3Blue1Brown YouTube channel: https://www.youtube.com/@3blue1brown
      """)
  @Agent(
      name = "ConversationAgent",
      description = "Handles general conversation and tutoring with students",
      outputKey = "response")
  @UserMessage("{{message}}")
  String process(@MemoryId String sessionId, @V("message") String message);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result(
        "I apologize, but I'm having trouble processing your request right now. "
        + "Please try again in a moment.");
  }

  @Tool("Searches the web for the given query and returns relevant result links. "
      + "Returns an empty list when web search is currently unavailable.")
  default List<String> searchWeb(String query) {
    return List.of();
  }
}
