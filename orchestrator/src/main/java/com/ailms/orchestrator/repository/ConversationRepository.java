package com.ailms.orchestrator.repository;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.entity.ConversationLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ConversationRepository implements PanacheRepository<ConversationLog> {

  public void logMessage(String userId, String sessionId, String role, String message) {
    logMessage(userId, sessionId, role, message, null);
  }

  public void logMessage(
      String userId, String sessionId, String role, String message, String agentType) {
    ConversationLog logEntry = new ConversationLog();
    logEntry.userId = userId;
    logEntry.sessionId = sessionId;
    logEntry.role = role;
    logEntry.message = message;
    logEntry.agentType = agentType;
    persist(logEntry);
  }

  public ChatHistory getHistory(String sessionId) {
    List<ConversationLog> logs =
        find("sessionId = ?1 ORDER BY timestamp ASC", sessionId).list();

    List<ChatHistory.ChatMessage> messages =
        logs.stream()
            .map(
                log ->
                    new ChatHistory.ChatMessage(
                        log.role,
                        "user".equals(log.role) ? log.message : log.assistantMessage,
                        log.agentType))
            .toList();

    return new ChatHistory(sessionId, messages);
  }

  public List<String> findInactiveUsersSince(Instant since) {
    return find(
            "SELECT DISTINCT userId FROM ConversationLog WHERE timestamp >= ?1 GROUP BY userId HAVING MAX(timestamp) < ?1",
            since)
        .list();
  }

  public List<ConversationLog> findRecentByUserId(String userId, int limit) {
    return find("userId = ?1 ORDER BY timestamp DESC", userId).page(0, limit).list();
  }
}
