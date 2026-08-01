package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatHistory.ChatMessage;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.list.ListCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ChatHistoryCacheService {

  private static final String PREFIX = "history:";
  private static final long TTL_SECONDS = 3600;

  private final ListCommands<String, String> lists;
  private final KeyCommands<String> keys;

  @Inject
  public ChatHistoryCacheService(RedisDataSource redisDS) {
    this.lists = redisDS.list(String.class);
    this.keys = redisDS.key();
  }

  public void cacheMessage(
      String userId, String sessionId, String role, String message, String agentType) {
    String key = PREFIX + userId + ":" + sessionId;
    String entry = String.join("||", role, message != null ? message : "", agentType != null ? agentType : "");
    lists.rpush(key, entry);
    keys.expire(key, TTL_SECONDS);
  }

  public ChatHistory getCachedHistory(String userId, String sessionId) {
    String key = PREFIX + userId + ":" + sessionId;
    List<String> entries = lists.lrange(key, 0, -1);
    if (entries.isEmpty()) return null;

    List<ChatMessage> messages = entries.stream()
        .map(e -> {
          String[] parts = e.split("\\|\\|", 3);
          return new ChatMessage(parts[0], parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : null);
        })
        .toList();
    return new ChatHistory(sessionId, messages);
  }
}
