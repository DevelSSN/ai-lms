package com.ailms.common.dto;

import java.util.List;
import java.util.Map;

/** Payload for submitting a completed quiz attempt. */
public record QuizResultRequest(
    String sessionId,
    String contentId,
    List<QuizItem> questions,
    Map<String, String> answers,
    int score,
    int total) {}