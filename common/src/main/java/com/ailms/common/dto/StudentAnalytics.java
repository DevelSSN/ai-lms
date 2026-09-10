package com.ailms.common.dto;

import java.time.Instant;
import java.util.List;

/** Aggregated learning analytics for a single student. */
public record StudentAnalytics(
    String studentId,
    List<String> topics,
    long conversationCount,
    long messageCount,
    long documentsAnalyzed,
    double averageScore,
    int quizAttempts,
    Instant lastActive) {}