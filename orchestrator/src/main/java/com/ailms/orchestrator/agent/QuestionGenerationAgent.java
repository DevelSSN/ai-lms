package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class QuestionGenerationAgent {

  public ChatResponse process(ChatRequest request) {
    log.debug("Generating questions session={}", request.sessionId());
    return new ChatResponse(
        "Generated assessment questions based on your content.", request.sessionId(), "assessment");
  }
}
