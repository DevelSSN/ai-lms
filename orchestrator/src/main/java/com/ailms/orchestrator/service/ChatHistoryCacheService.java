package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatHistory.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.list.ListCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ChatHistoryCacheService {

  private static final String PREFIX = "history:";
  private static final long TTL_SECONDS = 3600;

  private final ListCommands<String, String> lists;
  private final KeyCommands<String> keys;
  private final ObjectMapper objectMapper;

  @Inject
  public ChatHistoryCacheService(RedisDataSource redisDS, ObjectMapper objectMapper) {
    this.lists = redisDS.list(String.class);
    this.keys = redisDS.key();
    this.objectMapper = objectMapper;
  }

  public void cacheMessage(
      String userId, String sessionId, String role, String message, String agentType) {
    cacheMessage(userId, sessionId, role, message, agentType, Instant.now());
  }

  public void cacheMessage(
      String userId,
      String sessionId,
      String role,
      String message,
      String agentType,
      Instant timestamp) {
    String key = PREFIX + userId + ":" + sessionId;
    CacheEntry entry =
        new CacheEntry(role, message, agentType, timestamp != null ? timestamp.toString() : null);
    try {
      lists.rpush(key, objectMapper.writeValueAsString(entry));
      keys.expire(key, TTL_SECONDS);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize chat message for cache", e);
    }
  }

  public ChatHistory getCachedHistory(String userId, String sessionId) {
    String key = PREFIX + userId + ":" + sessionId;
    List<String> entries = lists.lrange(key, 0, -1);
    if (entries.isEmpty()) return null;

    List<ChatMessage> messages =
        entries.stream()
            .map(
                e -> {
                  try {
                    CacheEntry entry = objectMapper.readValue(e, CacheEntry.class);
                    return new ChatMessage(
                        entry.role(),
                        entry.content(),
                        entry.agentType(),
                        entry.timestamp() != null ? Instant.parse(entry.timestamp()) : null);
                  } catch (JsonProcessingException ex) {
                    throw new IllegalStateException(
                        "Failed to deserialize cached chat message", ex);
                  }
                })
            .toList();
    return new ChatHistory(sessionId, messages);
  }

  public void deleteHistory(String userId, String sessionId) {
    keys.del(PREFIX + userId + ":" + sessionId);
  }

  private record CacheEntry(String role, String content, String agentType, String timestamp) {}
}
