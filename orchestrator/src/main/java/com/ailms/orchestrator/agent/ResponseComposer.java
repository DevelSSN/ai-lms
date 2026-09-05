package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatResponse;
import com.ailms.common.enums.IntentType;
import dev.langchain4j.agentic.scope.AgenticScope;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ResponseComposer {

  public ChatResponse compose(AgenticScope agenticScope, String sessionId) {
    String intent = agenticScope.readState("intent", IntentType.CONVERSATION.name());
    String response = extractResponse(agenticScope, intent);

    return new ChatResponse(response, sessionId, intent);
  }

  private String extractResponse(AgenticScope agenticScope, String intent) {
    String primary = agenticScope.readState("response", "");
    if (primary != null && !primary.isBlank()) return primary;

    return switch (IntentType.fromName(intent)) {
      case CONTENT_ANALYSIS ->
          fallback(
              agenticScope.readState("analysis", ""),
              "I couldn't analyze because no analyzable content was provided. "
                  + "Upload a file or paste text first.");
      case ASSESSMENT ->
          fallback(
              agenticScope.readState("assessment", ""),
              "I couldn't generate questions because no content was provided. "
                  + "Upload a file or paste text first.");
      case INSIGHT ->
          fallback(
              agenticScope.readState("insights", ""),
              "Not enough data to generate insights yet. Complete a few lessons and try again.");
      default ->
          fallback(primary, "I couldn't generate a response. Please try rephrasing your question.");
    };
  }

  private String fallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
