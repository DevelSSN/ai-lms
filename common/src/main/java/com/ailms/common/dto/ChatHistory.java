package com.ailms.common.dto;

import java.time.Instant;
import java.util.List;

public record ChatHistory(String sessionId, List<ChatMessage> messages) {
  public record ChatMessage(
      String role, String content, String agentType, Instant timestamp, Object metadata) {
    public ChatMessage(String role, String content, String agentType, Instant timestamp) {
      this(role, content, agentType, timestamp, null);
    }

    public ChatMessage(String role, String content, String agentType) {
      this(role, content, agentType, null, null);
    }
  }
}
