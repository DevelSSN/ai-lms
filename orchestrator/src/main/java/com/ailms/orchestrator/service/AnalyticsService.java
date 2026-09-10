package com.ailms.orchestrator.service;

import com.ailms.common.dto.StudentAnalytics;
import com.ailms.common.entity.QuizResult;
import com.ailms.common.entity.UserProfile;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.QuizResultRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
}