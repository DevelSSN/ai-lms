package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailms.common.dto.StudentAnalytics;
import com.ailms.common.entity.QuizResult;
import com.ailms.common.entity.UserProfile;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.QuizResultRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  @Mock ConversationRepository conversations;

  @Mock QuizResultRepository quizzes;

  @Mock UserProfileRepository profiles;

  @Mock ContentDocumentService documents;

  @InjectMocks AnalyticsService service;

  @Test
  void student_aggregatesAcrossSources() {
    QuizResult q1 = new QuizResult();
    q1.userId = "s1";
    q1.score = 8;
    q1.total = 10;
    QuizResult q2 = new QuizResult();
    q2.userId = "s1";
    q2.score = 4;
    q2.total = 10;
    when(quizzes.list("userId", "s1")).thenReturn(List.of(q1, q2));
    when(documents.countByUser("s1")).thenReturn(3L);
    when(conversations.countMessages("s1")).thenReturn(42L);
    when(conversations.countSessions("s1")).thenReturn(5L);
    when(conversations.lastActivity("s1")).thenReturn(Instant.parse("2026-09-01T10:00:00Z"));

    UserProfile profile = new UserProfile();
    profile.externalId = "s1";
    profile.interests = "physics, math; biology";
    stubProfile("s1", profile);

    StudentAnalytics result = service.student("s1");

    assertEquals("s1", result.studentId());
    assertEquals(5L, result.conversationCount());
    assertEquals(42L, result.messageCount());
    assertEquals(3L, result.documentsAnalyzed());
    assertEquals(2, result.quizAttempts());
    assertEquals(60.0, result.averageScore());
    assertEquals(List.of("physics", "math", "biology"), result.topics());
    assertEquals(Instant.parse("2026-09-01T10:00:00Z"), result.lastActive());
  }

  @Test
  void student_handlesMissingData() {
    when(quizzes.list("userId", "s1")).thenReturn(List.of());
    when(documents.countByUser("s1")).thenReturn(0L);
    when(conversations.countMessages("s1")).thenReturn(0L);
    when(conversations.countSessions("s1")).thenReturn(0L);
    when(conversations.lastActivity("s1")).thenReturn(null);
    stubProfile("s1", null);

    StudentAnalytics result = service.student("s1");

    assertEquals(0, result.quizAttempts());
    assertEquals(0.0, result.averageScore());
    assertEquals(0, result.topics().size());
    assertNull(result.lastActive());
  }

  @SuppressWarnings("unchecked")
  private void stubProfile(String studentId, UserProfile profile) {
    PanacheQuery<UserProfile> q = mock(PanacheQuery.class);
    when(q.firstResult()).thenReturn(profile);
    when(profiles.find("externalId", studentId)).thenReturn(q);
  }
}