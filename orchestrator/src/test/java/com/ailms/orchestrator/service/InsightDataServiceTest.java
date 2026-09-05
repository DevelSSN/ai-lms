package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ThreadSummary;
import com.ailms.common.entity.UserProfile;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsightDataServiceTest {

  @Mock ConversationRepository conversationRepository;
  @Mock UserProfileRepository userProfileRepository;

  private InsightDataService build() {
    InsightDataService svc = new InsightDataService();
    svc.conversationRepository = conversationRepository;
    svc.userProfileRepository = userProfileRepository;
    return svc;
  }

  @Test
  void buildContext_includesProfileAndSessionStats() {
    Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
    Instant t2 = Instant.parse("2026-01-01T11:30:00Z");
    when(userProfileRepository.findByExternalId("user-1"))
        .thenReturn(withProfile("beginner", "Math, Signals"));
    when(conversationRepository.getHistory("user-1", "sess-1"))
        .thenReturn(
            new ChatHistory(
                "sess-1",
                List.of(
                    new ChatHistory.ChatMessage("user", "hello", null, t1),
                    new ChatHistory.ChatMessage("assistant", "Hi!", "CONVERSATION", t2),
                    new ChatHistory.ChatMessage(
                        "user", "Analyze the uploaded file: doc-9", null, t2))));
    when(conversationRepository.listThreads("user-1"))
        .thenReturn(List.of(new ThreadSummary("sess-1", "Intro", t2, 3)));

    InsightDataService svc = build();
    String ctx = svc.buildContext("user-1", "sess-1");

    assertTrue(ctx.contains("Knowledge level: beginner"));
    assertTrue(ctx.contains("Interests: Math, Signals"));
    assertTrue(ctx.contains("Messages in this session: 3"));
    assertTrue(ctx.contains("Student messages: 2"));
    assertTrue(ctx.contains("Assistant messages: 1"));
    assertTrue(ctx.contains("Files analyzed in this session: 1"));
    assertTrue(ctx.contains("Session start: 2026-01-01T10:00:00Z"));
    assertTrue(ctx.contains("Total conversation threads: 1"));
    assertTrue(ctx.contains("Total messages across threads: 3"));
  }

  @Test
  void buildContext_handlesEmptyProfileAndHistory() {
    when(userProfileRepository.findByExternalId("user-1")).thenReturn(null);
    when(conversationRepository.getHistory("user-1", "sess-1"))
        .thenReturn(new ChatHistory("sess-1", List.of()));
    when(conversationRepository.listThreads("user-1")).thenReturn(List.of());

    InsightDataService svc = build();
    String ctx = svc.buildContext("user-1", "sess-1");

    assertNotNull(ctx);
    assertTrue(ctx.contains("Messages in this session: 0"));
    assertTrue(ctx.contains("Total conversation threads: 0"));
  }

  @Test
  void buildContext_nullUserId_returnsNull() {
    InsightDataService svc = build();
    assertNull(svc.buildContext(null, "sess-1"));
    verifyNoInteractions(userProfileRepository);
    verifyNoInteractions(conversationRepository);
  }

  @Test
  void buildContext_repoFailure_stillReturnsSummary() {
    when(userProfileRepository.findByExternalId("user-1")).thenReturn(null);
    when(conversationRepository.getHistory("user-1", "sess-1"))
        .thenThrow(new RuntimeException("redis down"));
    when(conversationRepository.listThreads("user-1")).thenThrow(new RuntimeException("db down"));

    InsightDataService svc = build();
    String ctx = svc.buildContext("user-1", "sess-1");

    assertNotNull(ctx);
    assertTrue(ctx.contains("Messages in this session: 0"));
  }

  private UserProfile withProfile(String level, String interests) {
    UserProfile p = new UserProfile();
    p.knowledgeLevel = level;
    p.interests = interests;
    return p;
  }
}
