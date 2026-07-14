package com.ailms.common.dto;

import jakarta.validation.constraints.NotBlank;

public record AssessmentRequest(
    @NotBlank String contentId,
    String userId,
    Integer questionCount,
    String difficulty
) {}
