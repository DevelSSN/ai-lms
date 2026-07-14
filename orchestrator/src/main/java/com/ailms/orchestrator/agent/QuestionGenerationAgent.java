package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuestionGenerationAgent {

  public ChatResponse process(ChatRequest request) {
    return new ChatResponse(
        "Generated assessment questions based on your content.", request.sessionId(), "assessment");
  }
}
