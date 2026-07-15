package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class InsightAgent {

  public ChatResponse process(ChatRequest request) {
    log.debug("Generating insights session={}", request.sessionId());
    return new ChatResponse(
        "Here are your learning insights and progress report.", request.sessionId(), "insight");
  }
}
