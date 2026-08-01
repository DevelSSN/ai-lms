package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ailms.common.dto.ChatHistory;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.list.ListCommands;
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

  @Test
  void cacheMessage() {
    when(redisDS.list(String.class)).thenReturn(lists);
    when(redisDS.key()).thenReturn(keys);

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS);
    cache.cacheMessage("user-1", "sess-1", "user", "hello", null);

    verify(lists).rpush("history:user-1:sess-1", "user||hello||");
    verify(keys).expire(eq("history:user-1:sess-1"), anyLong());
  }

  @Test
  void cacheMessageWithAgentType() {
    when(redisDS.list(String.class)).thenReturn(lists);
    when(redisDS.key()).thenReturn(keys);

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS);
    cache.cacheMessage("user-1", "sess-1", "assistant", "hi there", "CONVERSATION");

    verify(lists).rpush("history:user-1:sess-1", "assistant||hi there||CONVERSATION");
  }

  @Test
  void getCachedHistory_returnsMessages() {
    when(redisDS.list(String.class)).thenReturn(lists);
    when(redisDS.key()).thenReturn(keys);
    when(lists.lrange("history:user-1:sess-1", 0, -1))
        .thenReturn(List.of("user||hello||", "assistant||hi||CONVERSATION"));

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS);
    ChatHistory history = cache.getCachedHistory("user-1", "sess-1");

    assertNotNull(history);
    assertEquals("sess-1", history.sessionId());
    assertEquals(2, history.messages().size());
    assertEquals("user", history.messages().get(0).role());
    assertEquals("CONVERSATION", history.messages().get(1).agentType());
  }

  @Test
  void getCachedHistory_emptyReturnsNull() {
    when(redisDS.list(String.class)).thenReturn(lists);
    when(redisDS.key()).thenReturn(keys);
    when(lists.lrange("history:user-1:sess-1", 0, -1)).thenReturn(List.of());

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS);
    assertNull(cache.getCachedHistory("user-1", "sess-1"));
  }
}
