package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ailms.common.dto.ChatHistory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.list.ListCommands;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatHistoryCacheServiceTest {

  @Mock RedisDataSource redisDS;
  @Mock ListCommands<String, String> lists;
  @Mock KeyCommands<String> keys;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void cacheMessage() {
    when(redisDS.list(String.class)).thenReturn(lists);
    when(redisDS.key()).thenReturn(keys);

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    cache.cacheMessage("user-1", "sess-1", "user", "hello", null);

    verify(lists)
        .rpush(eq("history:user-1:sess-1"), startsWith("{\"role\":\"user\",\"content\":\"hello\""));
    verify(keys).expire(eq("history:user-1:sess-1"), anyLong());
  }

  @Test
  void getCachedHistory_returnsMessages() {
    when(redisDS.list(String.class)).thenReturn(lists);
    when(redisDS.key()).thenReturn(keys);
    String ts = "2026-09-05T10:00:00Z";
    when(lists.lrange("history:user-1:sess-1", 0, -1))
        .thenReturn(
            List.of(
                "{\"role\":\"user\",\"content\":\"hello\",\"agentType\":null,\"timestamp\":\""
                    + ts
                    + "\"}",
                "{\"role\":\"assistant\",\"content\":\"hi\",\"agentType\":\"CONVERSATION\",\"timestamp\":\""
                    + ts
                    + "\"}"));

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    ChatHistory history = cache.getCachedHistory("user-1", "sess-1");

    assertNotNull(history);
    assertEquals("sess-1", history.sessionId());
    assertEquals(2, history.messages().size());
    assertEquals("user", history.messages().get(0).role());
    assertEquals("CONVERSATION", history.messages().get(1).agentType());
    assertEquals(Instant.parse(ts), history.messages().get(1).timestamp());
  }

  @Test
  void getCachedHistory_handlesDelimiterInContent() {
    when(redisDS.list(String.class)).thenReturn(lists);
    when(redisDS.key()).thenReturn(keys);
    String ts = "2026-09-05T10:00:00Z";
    when(lists.lrange("history:user-1:sess-1", 0, -1))
        .thenReturn(
            List.of(
                "{\"role\":\"user\",\"content\":\"a || b ||"
                    + " c\",\"agentType\":\"CONVERSATION\",\"timestamp\":\""
                    + ts
                    + "\"}"));

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    ChatHistory history = cache.getCachedHistory("user-1", "sess-1");

    assertNotNull(history);
    assertEquals(1, history.messages().size());
    assertEquals("a || b || c", history.messages().get(0).content());
    assertEquals("CONVERSATION", history.messages().get(0).agentType());
    assertEquals(Instant.parse(ts), history.messages().get(0).timestamp());
  }

  @Test
  void getCachedHistory_emptyReturnsNull() {
    when(redisDS.list(String.class)).thenReturn(lists);
    when(redisDS.key()).thenReturn(keys);
    when(lists.lrange("history:user-1:sess-1", 0, -1)).thenReturn(List.of());

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    assertNull(cache.getCachedHistory("user-1", "sess-1"));
  }
}
