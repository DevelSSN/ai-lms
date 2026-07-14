package com.ailms.common.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
    @NotBlank String message,
    String sessionId,
    String userId
) {}
