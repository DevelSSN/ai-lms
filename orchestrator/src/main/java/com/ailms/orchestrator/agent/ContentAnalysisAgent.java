package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ContentAnalysisAgent {

  public ChatResponse process(ChatRequest request) {
    return new ChatResponse(
        "Content analysis complete. I've identified key topics and concepts.",
        request.sessionId(),
        "content_analysis");
  }
}
