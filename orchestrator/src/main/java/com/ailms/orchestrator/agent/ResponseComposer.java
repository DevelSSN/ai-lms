package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ResponseComposer {

  public ChatResponse compose(AgenticScope agenticScope, String sessionId) {
    String intent = agenticScope.readState("intent", "CONVERSATION");
    String response = extractResponse(agenticScope, intent);

    return new ChatResponse(response, sessionId, intent);
  }

  private String extractResponse(AgenticScope agenticScope, String intent) {
    return switch (intent) {
      case "CONTENT_ANALYSIS" -> fallback(agenticScope.readState("analysis", ""), "Analysis not available");
      case "ASSESSMENT" -> fallback(agenticScope.readState("assessment", ""), "Assessment not available");
      case "INSIGHT" -> fallback(agenticScope.readState("insights", ""), "Insights not available");
      default -> fallback(
          agenticScope.readState("response", ""),
          "I couldn't generate a response. Please try rephrasing your question.");
    };
  }

  private String fallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
