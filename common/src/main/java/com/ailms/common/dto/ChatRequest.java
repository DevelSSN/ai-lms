package com.ailms.common.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message, String sessionId, Boolean bypassRoutes) {

  public ChatRequest(String message, String sessionId) {
    this(message, sessionId, null);
  }
}
