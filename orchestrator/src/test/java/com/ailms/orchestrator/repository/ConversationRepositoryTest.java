package com.ailms.orchestrator.repository;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.entity.ConversationLog;
import com.ailms.orchestrator.service.ChatHistoryCacheService;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationRepositoryTest {

  @Mock EntityManager em;
  @Mock ChatHistoryCacheService historyCache;

  @Test
  void logUserMessage() {
    ConversationRepository repo = new ConversationRepository();
    repo.em = em;
    repo.historyCache = historyCache;

    repo.logMessage("user-1", "sess-1", "user", "hello");
    verify(historyCache).cacheMessage("sess-1", "user", "hello", null);
  }

  @Test
  void logAssistantMessageWithAgentType() {
    ConversationRepository repo = new ConversationRepository();
    repo.em = em;
    repo.historyCache = historyCache;

    repo.logMessage("user-1", "sess-1", "assistant", "Hello!", "CONVERSATION");
    verify(historyCache).cacheMessage("sess-1", "assistant", "Hello!", "CONVERSATION");
  }

  @Test
  void getHistory_fromCache() {
    when(historyCache.getCachedHistory("sess-1"))
        .thenReturn(new ChatHistory("sess-1", List.of()));

    ConversationRepository repo = new ConversationRepository();
    repo.historyCache = historyCache;

    ChatHistory history = repo.getHistory("sess-1");
    assertNotNull(history);
    assertEquals("sess-1", history.sessionId());
    verify(em, never()).createQuery(anyString());
  }

  @Test
  void getHistory_fallbackToDb() {
    when(historyCache.getCachedHistory("sess-1")).thenReturn(null);

    java.util.List<ConversationLog> dbLogs = List.of();
    var query = mock(jakarta.persistence.TypedQuery.class);
    when(query.getResultList()).thenReturn(dbLogs);
    when(em.createQuery(anyString(), eq(ConversationLog.class))).thenReturn(query);

    ConversationRepository repo = new ConversationRepository();
    repo.em = em;
    repo.historyCache = historyCache;

    ChatHistory history = repo.getHistory("sess-1");
    assertNotNull(history);
    assertTrue(history.messages().isEmpty());
  }
}
