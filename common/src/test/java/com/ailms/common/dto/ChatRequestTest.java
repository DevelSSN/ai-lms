package com.ailms.common.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatRequestTest {

  @Test
  void testCreate() {
    ChatRequest req = new ChatRequest("hello", "sess-1");
    assertEquals("hello", req.message());
    assertEquals("sess-1", req.sessionId());
  }

  @Test
  void testChatResponse() {
    ChatResponse res = new ChatResponse("hi", "sess-1", "CONVERSATION");
    assertEquals("hi", res.message());
    assertEquals("sess-1", res.sessionId());
    assertEquals("CONVERSATION", res.agentType());
  }

  @Test
  void testChatHistory() {
    var msg = new ChatHistory.ChatMessage("user", "hello", null);
    var hist = new ChatHistory("sess-1", java.util.List.of(msg));
    assertEquals("sess-1", hist.sessionId());
    assertEquals(1, hist.messages().size());
  }
}
