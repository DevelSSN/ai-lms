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
      case "CONTENT_ANALYSIS" -> agenticScope.readState("analysis", "Analysis not available");
      case "ASSESSMENT" -> agenticScope.readState("assessment", "Assessment not available");
      case "INSIGHT" -> agenticScope.readState("insights", "Insights not available");
      default -> agenticScope.readState("response", "No response generated");
    };
  }
}
