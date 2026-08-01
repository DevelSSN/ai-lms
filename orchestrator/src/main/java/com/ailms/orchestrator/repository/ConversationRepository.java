package com.ailms.orchestrator.repository;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ThreadSummary;
import com.ailms.common.entity.ConversationLog;
import com.ailms.orchestrator.service.ChatHistoryCacheService;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ConversationRepository implements PanacheRepository<ConversationLog> {

  @Inject EntityManager em;

  @Inject ChatHistoryCacheService historyCache;

  @Transactional
  public void logMessage(String userId, String sessionId, String role, String message) {
    logMessage(userId, sessionId, role, message, null);
  }

  @Transactional
  public void logMessage(
      String userId, String sessionId, String role, String message, String agentType) {
    ConversationLog logEntry = new ConversationLog();
    logEntry.userId = userId;
    logEntry.sessionId = sessionId;
    logEntry.role = role;
    logEntry.message = message;
    logEntry.assistantMessage = "assistant".equals(role) ? message : null;
    logEntry.agentType = agentType;
    persist(logEntry);

    historyCache.cacheMessage(userId, sessionId, role, message, agentType);
  }

  public ChatHistory getHistory(String userId, String sessionId) {
    ChatHistory cached = historyCache.getCachedHistory(userId, sessionId);
    if (cached != null) return cached;

    List<ConversationLog> logs =
        find("userId = ?1 AND sessionId = ?2 ORDER BY timestamp ASC", userId, sessionId).list();

    List<ChatHistory.ChatMessage> messages =
        logs.stream()
            .map(log ->
                new ChatHistory.ChatMessage(
                    log.role,
                    "user".equals(log.role)
                        ? log.message
                        : log.assistantMessage != null ? log.assistantMessage : log.message,
                    log.agentType))
            .toList();

    return new ChatHistory(sessionId, messages);
  }

  @SuppressWarnings("unchecked")
  public List<ThreadSummary> listThreads(String userId) {
    List<Object[]> rows =
        em.createQuery(
                "SELECT l.sessionId, MAX(l.timestamp), COUNT(l) FROM ConversationLog l "
                    + "WHERE l.userId = :userId GROUP BY l.sessionId "
                    + "ORDER BY MAX(l.timestamp) DESC")
            .setParameter("userId", userId)
            .getResultList();

    Map<String, String> titles = threadTitles(userId);

    return rows.stream()
        .map(row ->
            new ThreadSummary(
                (String) row[0],
                titles.get(row[0]),
                (Instant) row[1],
                ((Number) row[2]).longValue()))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> threadTitles(String userId) {
    Map<String, String> titles = new LinkedHashMap<>();
    List<Object[]> rows =
        em.createQuery(
                "SELECT l.sessionId, l.title FROM ConversationLog l "
                    + "WHERE l.userId = :userId AND l.title IS NOT NULL "
                    + "ORDER BY l.timestamp ASC")
            .setParameter("userId", userId)
            .getResultList();
    for (Object[] row : rows) {
      titles.putIfAbsent((String) row[0], (String) row[1]);
    }
    return titles;
  }

  @Transactional
  public void setThreadTitle(String userId, String sessionId, String title) {
    if (title == null || title.isBlank()) return;
    update(
        "set title = ?1 where userId = ?2 and sessionId = ?3",
        title.trim().substring(0, Math.min(title.trim().length(), 60)),
        userId,
        sessionId);
  }

  public ConversationLog firstUserMessage(String sessionId) {
    return find("sessionId = ?1 AND role = 'user' ORDER BY timestamp ASC", sessionId).firstResult();
  }

  @SuppressWarnings("unchecked")
  public List<String> findInactiveUsersSince(Instant since) {
    return em.createQuery(
            "SELECT DISTINCT userId FROM ConversationLog GROUP BY userId HAVING MAX(timestamp) < :since")
        .setParameter("since", since)
        .getResultList();
  }

  public List<ConversationLog> findRecentByUserId(String userId, int limit) {
    return find("userId = ?1 ORDER BY timestamp DESC", userId).page(0, limit).list();
  }
}
