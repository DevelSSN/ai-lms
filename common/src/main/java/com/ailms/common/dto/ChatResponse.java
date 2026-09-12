package com.ailms.common.dto;

import java.time.Instant;

public record ChatResponse(
    String message, String sessionId, String agentType, Object metadata, Instant timestamp) {
  public ChatResponse(String message, String sessionId, String agentType, Object metadata) {
    this(message, sessionId, agentType, metadata, null);
  }

  public ChatResponse(String message, String sessionId, String agentType) {
    this(message, sessionId, agentType, null, null);
  }
}