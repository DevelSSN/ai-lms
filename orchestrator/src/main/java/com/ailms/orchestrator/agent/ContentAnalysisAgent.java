package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContentAnalysisAgent {

  public ChatResponse process(ChatRequest request) {
    log.debug("Processing content analysis session={}", request.sessionId());
    return new ChatResponse(
        "Content analysis complete. I've identified key topics and concepts.",
        request.sessionId(),
        "content_analysis");
  }
}
