package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InsightAgent {

  public ChatResponse process(ChatRequest request) {
    return new ChatResponse(
        "Here are your learning insights and progress report.", request.sessionId(), "insight");
  }
}
