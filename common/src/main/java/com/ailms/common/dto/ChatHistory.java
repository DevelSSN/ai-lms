package com.ailms.common.dto;

import java.util.List;

public record ChatHistory(String sessionId, List<ChatMessage> messages) {
  public record ChatMessage(String role, String content, String agentType) {}
}
