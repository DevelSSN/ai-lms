package com.ailms.common.dto;

public record ChatResponse(String message, String sessionId, String agentType, Object metadata) {
  public ChatResponse(String message, String sessionId, String agentType) {
    this(message, sessionId, agentType, null);
  }
}
