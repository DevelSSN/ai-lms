package com.ailms.orchestrator.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class RedisChatMemoryStore implements ChatMemoryStore {

  private static final String KEY_PREFIX = "chat:memory:";
  private static final long TTL_SECONDS = 86400;

  private final ValueCommands<String, String> values;
  private final KeyCommands<String> keys;

  @Inject
  public RedisChatMemoryStore(RedisDataSource redisDS) {
    this.values = redisDS.value(String.class);
    this.keys = redisDS.key();
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    String raw = values.get(KEY_PREFIX + memoryId);
    if (raw == null || raw.isBlank()) return List.of();
    return ChatMessageDeserializer.messagesFromJson(raw);
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    String key = KEY_PREFIX + memoryId;
    String json = ChatMessageSerializer.messagesToJson(messages);
    values.set(key, json);
    keys.expire(key, TTL_SECONDS);
  }

  @Override
  public void deleteMessages(Object memoryId) {
    keys.del(KEY_PREFIX + memoryId);
  }
}
