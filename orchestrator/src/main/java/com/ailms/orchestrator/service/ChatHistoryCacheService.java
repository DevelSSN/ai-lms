package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatHistory.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.list.ListCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ChatHistoryCacheService {

  private static final String PREFIX = "history:";
  private static final String SEEDED_SUFFIX = ":seeded";
  private static final long TTL_SECONDS = 3600;

  private final ListCommands<String, String> lists;
  private final KeyCommands<String> keys;
  private final ValueCommands<String, String> values;
  private final ObjectMapper objectMapper;

  @Inject
  public ChatHistoryCacheService(RedisDataSource redisDS, ObjectMapper objectMapper) {
    this.lists = redisDS.list(String.class);
    this.keys = redisDS.key();
    this.values = redisDS.value(String.class);
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
    // Only append to a cache that holds the full history. After the key expires,
    // a bare rpush would create a partial list that shadows the database on reads.
    if (!Boolean.TRUE.equals(keys.exists(key + SEEDED_SUFFIX))) {
      return;
    }
    CacheEntry entry =
        new CacheEntry(role, message, agentType, timestamp != null ? timestamp.toString() : null);
    try {
      lists.rpush(key, objectMapper.writeValueAsString(entry));
      keys.expire(key, TTL_SECONDS);
      keys.expire(key + SEEDED_SUFFIX, TTL_SECONDS);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize chat message for cache", e);
    }
  }

  /** Stores the full history loaded from the database and marks the cache as seeded. */
  public void seedHistory(String userId, String sessionId, List<ChatMessage> messages) {
    String key = PREFIX + userId + ":" + sessionId;
    String seededKey = key + SEEDED_SUFFIX;
    try {
      keys.del(key);
      for (ChatMessage message : messages) {
        CacheEntry entry =
            new CacheEntry(
                message.role(),
                message.content(),
                message.agentType(),
                message.timestamp() != null ? message.timestamp().toString() : null);
        lists.rpush(key, objectMapper.writeValueAsString(entry));
      }
      values.set(seededKey, "1");
      if (!messages.isEmpty()) {
        keys.expire(key, TTL_SECONDS);
      }
      keys.expire(seededKey, TTL_SECONDS);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize chat history for cache", e);
    }
  }

  public ChatHistory getCachedHistory(String userId, String sessionId) {
    String key = PREFIX + userId + ":" + sessionId;
    if (!Boolean.TRUE.equals(keys.exists(key + SEEDED_SUFFIX))) {
      return null;
    }
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
    keys.del(PREFIX + userId + ":" + sessionId + SEEDED_SUFFIX);
  }

  private record CacheEntry(String role, String content, String agentType, String timestamp) {}
}
