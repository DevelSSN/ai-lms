package com.ailms.orchestrator.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisChatMemoryStoreTest {

  @Mock RedisDataSource redisDS;
  @Mock ValueCommands<String, String> values;
  @Mock KeyCommands<String> keys;

  @Test
  void getMessages_empty() {
    when(redisDS.value(String.class)).thenReturn(values);
    when(redisDS.key()).thenReturn(keys);
    when(values.get("chat:memory:sess-1")).thenReturn(null);

    RedisChatMemoryStore store = new RedisChatMemoryStore(redisDS);
    assertTrue(store.getMessages("sess-1").isEmpty());
  }

  @Test
  void getMessages_blank() {
    when(redisDS.value(String.class)).thenReturn(values);
    when(redisDS.key()).thenReturn(keys);
    when(values.get("chat:memory:sess-1")).thenReturn("   ");

    RedisChatMemoryStore store = new RedisChatMemoryStore(redisDS);
    assertTrue(store.getMessages("sess-1").isEmpty());
  }

  @Test
  @Disabled("Needs Quarkus JsonCodecFactory")
  void updateAndGetMessages() {
    when(redisDS.value(String.class)).thenReturn(values);
    when(redisDS.key()).thenReturn(keys);

    RedisChatMemoryStore store = new RedisChatMemoryStore(redisDS);
    List<ChatMessage> msgs = List.of(UserMessage.from("hello"), AiMessage.from("hi"));
    store.updateMessages("sess-1", msgs);

    verify(values).set(eq("chat:memory:sess-1"), anyString());
    verify(keys).expire(eq("chat:memory:sess-1"), anyLong());
  }

  @Test
  void deleteMessages() {
    when(redisDS.value(String.class)).thenReturn(values);
    when(redisDS.key()).thenReturn(keys);

    RedisChatMemoryStore store = new RedisChatMemoryStore(redisDS);
    store.deleteMessages("sess-1");

    verify(keys).del("chat:memory:sess-1");
  }

  @Test
  @Disabled("Needs Quarkus JsonCodecFactory")
  void getMessages_deserializesCorrectly() {
    when(redisDS.value(String.class)).thenReturn(values);
    when(redisDS.key()).thenReturn(keys);

    String json = "[{\"type\":\"AI\",\"text\":\"Hello!\"}]";
    when(values.get("chat:memory:sess-1")).thenReturn(json);

    RedisChatMemoryStore store = new RedisChatMemoryStore(redisDS);
    List<ChatMessage> msgs = store.getMessages("sess-1");

    assertEquals(1, msgs.size());
    assertInstanceOf(AiMessage.class, msgs.get(0));
    assertEquals("Hello!", ((AiMessage) msgs.get(0)).text());
  }
}
