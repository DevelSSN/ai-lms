package com.ailms.orchestrator.service;

import com.ailms.common.dto.ClassAnalytics;
import com.ailms.common.dto.ClassAnalytics.TopicCoverage;
import com.ailms.common.dto.StudentAnalytics;
import com.ailms.common.entity.QuizResult;
import com.ailms.common.entity.UserProfile;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.QuizResultRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class AnalyticsService {

  @Inject ConversationRepository conversations;

  @Inject QuizResultRepository quizzes;

  @Inject UserProfileRepository profiles;

  @Inject ContentDocumentService documents;

  public StudentAnalytics student(String studentId) {
    long attempts = 0;
    double avg = 0;
    try {
      List<QuizResult> results = quizzes.list("userId", studentId);
      attempts = results.size();
      avg =
          results.stream()
              .mapToDouble(q -> q.total > 0 ? q.score * 100.0 / q.total : 0)
              .average()
              .orElse(0);
    } catch (Exception e) {
      log.warn("Quiz analytics failed for student={}: {}", studentId, e.getMessage());
    }

    long docs;
    long messages;
    long sessions;
    Instant lastActive;
    try {
      docs = documents.countByUser(studentId);
    } catch (Exception e) {
      log.warn("Document count failed for student={}: {}", studentId, e.getMessage());
      docs = 0;
    }
    try {
      messages = conversations.countMessages(studentId);
      sessions = conversations.countSessions(studentId);
      lastActive = conversations.lastActivity(studentId);
    } catch (Exception e) {
      log.warn("Conversation analytics failed for student={}: {}", studentId, e.getMessage());
      messages = 0;
      sessions = 0;
      lastActive = null;
    }

    return new StudentAnalytics(
        studentId,
        topicsFromProfile(studentId),
        sessions,
        messages,
        docs,
        Math.round(avg * 100.0) / 100.0,
        (int) attempts,
        lastActive);
  }

  private List<String> topicsFromProfile(String studentId) {
    try {
      UserProfile profile = profiles.find("externalId", studentId).firstResult();
      if (profile == null || profile.interests == null || profile.interests.isBlank()) {
        return List.of();
      }
      return Arrays.stream(profile.interests.split("[,;\\n]"))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .distinct()
          .limit(20)
          .toList();
    } catch (Exception e) {
      log.warn("Profile topics failed for student={}: {}", studentId, e.getMessage());
      return List.of();
    }
  }

  public ClassAnalytics classAnalytics() {
    long totalStudents;
    long activeStudents;
    long totalConversations;
    try {
      totalStudents = profiles.count();
    } catch (Exception e) {
      log.warn("Class student count failed: {}", e.getMessage());
      totalStudents = 0;
    }
    try {
      activeStudents =
          conversations.countActiveStudents(Instant.now().minus(30, ChronoUnit.DAYS));
    } catch (Exception e) {
      log.warn("Class active count failed: {}", e.getMessage());
      activeStudents = 0;
    }
    try {
      totalConversations = conversations.countAllSessions();
    } catch (Exception e) {
      log.warn("Class conversation count failed: {}", e.getMessage());
      totalConversations = 0;
    }

    double avg = 0;
    int attempts = 0;
    try {
      List<QuizResult> results = quizzes.listAll();
      attempts = results.size();
      avg =
          results.stream()
              .mapToDouble(q -> q.total > 0 ? q.score * 100.0 / q.total : 0)
              .average()
              .orElse(0);
    } catch (Exception e) {
      log.warn("Class quiz aggregation failed: {}", e.getMessage());
    }

    return new ClassAnalytics(
        totalStudents,
        activeStudents,
        totalConversations,
        Math.round(avg * 100.0) / 100.0,
        attempts,
        topicCoverage());
  }

  private List<TopicCoverage> topicCoverage() {
    Map<String, Long> counts = new LinkedHashMap<>();
    try {
      for (UserProfile profile : profiles.listAll()) {
        if (profile.interests == null || profile.interests.isBlank()) continue;
        Arrays.stream(profile.interests.split("[,;\\n]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .forEach(topic -> counts.merge(topic, 1L, Long::sum));
      }
    } catch (Exception e) {
      log.warn("Class topic coverage failed: {}", e.getMessage());
    }
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(10)
        .map(e -> new TopicCoverage(e.getKey(), e.getValue()))
        .toList();
  }
}