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
import io.quarkus.redis.datasource.value.ValueCommands;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatHistoryCacheServiceTest {

  @Mock RedisDataSource redisDS;
  @Mock ListCommands<String, String> lists;
  @Mock KeyCommands<String> keys;
  @Mock ValueCommands<String, String> values;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String KEY = "history:user-1:sess-1";
  private static final String SEEDED_KEY = KEY + ":seeded";

  @BeforeEach
  void setUp() {
    lenient().when(redisDS.list(String.class)).thenReturn(lists);
    lenient().when(redisDS.key()).thenReturn(keys);
    lenient().when(redisDS.value(String.class)).thenReturn(values);
  }

  @Test
  void cacheMessage_whenSeeded_appendsAndRefreshesTtl() {
    when(keys.exists(SEEDED_KEY)).thenReturn(true);

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    cache.cacheMessage("user-1", "sess-1", "user", "hello", null);

    verify(lists).rpush(eq(KEY), startsWith("{\"role\":\"user\",\"content\":\"hello\""));
    verify(keys).expire(eq(KEY), anyLong());
    verify(keys).expire(eq(SEEDED_KEY), anyLong());
  }

  @Test
  void cacheMessage_whenNotSeeded_skipsCache() {
    when(keys.exists(SEEDED_KEY)).thenReturn(false);

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    cache.cacheMessage("user-1", "sess-1", "user", "hello", null);

    verify(lists, never()).rpush(anyString(), anyString());
  }

  @Test
  void seedHistory_storesAllMessagesAndMarksSeeded() {
    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    Instant ts = Instant.parse("2026-09-05T10:00:00Z");
    cache.seedHistory(
        "user-1",
        "sess-1",
        List.of(
            new ChatHistory.ChatMessage("user", "hello", null, ts),
            new ChatHistory.ChatMessage("assistant", "hi", "CONVERSATION", ts)));

    verify(keys).del(eq(KEY));
    verify(lists, times(2)).rpush(eq(KEY), anyString());
    verify(values).set(eq(SEEDED_KEY), eq("1"));
    verify(keys).expire(eq(KEY), anyLong());
    verify(keys).expire(eq(SEEDED_KEY), anyLong());
  }

  @Test
  void getCachedHistory_returnsMessages() {
    when(keys.exists(SEEDED_KEY)).thenReturn(true);
    String ts = "2026-09-05T10:00:00Z";
    when(lists.lrange(KEY, 0, -1))
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
  void seedHistory_andGetCachedHistory_roundTripQuizMetadata() {
    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    Instant ts = Instant.parse("2026-09-05T10:00:00Z");
    Map<String, Object> metadata =
        Map.of("contentId", "doc-9", "items", List.of(Map.of("question", "What is 2+2?")));
    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

    cache.seedHistory(
        "user-1",
        "sess-1",
        List.of(new ChatHistory.ChatMessage("assistant", "[]", "ASSESSMENT", ts, metadata)));
    verify(lists).rpush(eq(KEY), payload.capture());

    when(keys.exists(SEEDED_KEY)).thenReturn(true);
    when(lists.lrange(KEY, 0, -1)).thenReturn(List.of(payload.getValue()));

    ChatHistory history = cache.getCachedHistory("user-1", "sess-1");

    assertNotNull(history);
    assertEquals("ASSESSMENT", history.messages().get(0).agentType());
    assertNotNull(history.messages().get(0).metadata());
    assertTrue(history.messages().get(0).metadata().toString().contains("doc-9"));
  }

  @Test
  void getCachedHistory_notSeededReturnsNull_evenWhenListHasEntries() {
    when(keys.exists(SEEDED_KEY)).thenReturn(false);

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    assertNull(cache.getCachedHistory("user-1", "sess-1"));
    verify(lists, never()).lrange(anyString(), anyLong(), anyLong());
  }

  @Test
  void getCachedHistory_handlesDelimiterInContent() {
    when(keys.exists(SEEDED_KEY)).thenReturn(true);
    String ts = "2026-09-05T10:00:00Z";
    when(lists.lrange(KEY, 0, -1))
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
    when(keys.exists(SEEDED_KEY)).thenReturn(true);
    when(lists.lrange(KEY, 0, -1)).thenReturn(List.of());

    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    assertNull(cache.getCachedHistory("user-1", "sess-1"));
  }

  @Test
  void deleteHistory_removesListAndSeedMarker() {
    ChatHistoryCacheService cache = new ChatHistoryCacheService(redisDS, objectMapper);
    cache.deleteHistory("user-1", "sess-1");

    verify(keys).del(eq(KEY));
    verify(keys).del(eq(SEEDED_KEY));
  }
}
