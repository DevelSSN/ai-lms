package com.ailms.common.dto;

import java.util.List;

/** Whole-class aggregated analytics, available to teachers and admins. */
public record ClassAnalytics(
    long totalStudents,
    long activeStudentsLast30Days,
    long totalConversations,
    double averageScore,
    int totalQuizAttempts,
    List<TopicCoverage> topics) {

  public record TopicCoverage(String topic, long studentCount) {}
}