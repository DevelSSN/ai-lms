package com.ailms.orchestrator.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the most recent topic a session has been about. Topicless follow-ups ("list videos",
 * "any more?") are answered against this running topic instead of their own literal text. Stored
 * best-effort: a Redis failure degrades to "no topic" and never breaks a turn.
 */
@Slf4j
@ApplicationScoped
public class SessionTopicStore {

  private static final String KEY_PREFIX = "chat:topic:";
  private static final long TTL_SECONDS = 86400;
  private static final int MAX_TOPIC_LENGTH = 300;

  private final ValueCommands<String, String> values;
  private final KeyCommands<String> keys;

  @Inject
  public SessionTopicStore(RedisDataSource redisDS) {
    this.values = redisDS.value(String.class);
    this.keys = redisDS.key();
  }

  public void setTopic(String sessionId, String topic) {
    if (isBlank(sessionId) || isBlank(topic)) return;
    String value = topic.strip();
    if (value.length() > MAX_TOPIC_LENGTH) {
      value = value.substring(0, MAX_TOPIC_LENGTH).strip();
    }
    try {
      values.set(key(sessionId), value);
      keys.expire(key(sessionId), TTL_SECONDS);
    } catch (Exception e) {
      log.warn("Failed to persist session topic for session={}: {}", sessionId, e.getMessage());
    }
  }

  public String getTopic(String sessionId) {
    if (isBlank(sessionId)) return null;
    try {
      String value = values.get(key(sessionId));
      return isBlank(value) ? null : value;
    } catch (Exception e) {
      log.warn("Failed to read session topic for session={}: {}", sessionId, e.getMessage());
      return null;
    }
  }

  private static String key(String sessionId) {
    return KEY_PREFIX + sessionId.trim();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
