package com.ailms.common.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class ChatRequestTest {

  @Test
  void createWithMessageAndSession() {
    ChatRequest req = new ChatRequest("hello", "sess-1");
    assertEquals("hello", req.message());
    assertEquals("sess-1", req.sessionId());
  }

  @Test
  void createWithNullSession() {
    ChatRequest req = new ChatRequest("hello", null);
    assertNull(req.sessionId());
  }
}

class ChatResponseTest {

  @Test
  void createWithAllFields() {
    ChatResponse res = new ChatResponse("hi", "sess-1", "CONVERSATION", null);
    assertEquals("hi", res.message());
    assertEquals("sess-1", res.sessionId());
    assertEquals("CONVERSATION", res.agentType());
    assertNull(res.metadata());
  }

  @Test
  void createWithoutMetadata() {
    ChatResponse res = new ChatResponse("hi", "sess-1", "CONVERSATION");
    assertNull(res.metadata());
  }

  @Test
  void createWithMetadata() {
    ChatResponse res = new ChatResponse("hi", "sess-1", "ANALYSIS", List.of("key1", "key2"));
    assertNotNull(res.metadata());
  }
}

class ChatHistoryTest {

  @Test
  void createEmpty() {
    ChatHistory h = new ChatHistory("sess-1", List.of());
    assertEquals("sess-1", h.sessionId());
    assertTrue(h.messages().isEmpty());
  }

  @Test
  void createWithMessages() {
    var msgs = List.of(
        new ChatHistory.ChatMessage("user", "hello", null),
        new ChatHistory.ChatMessage("assistant", "hi", "CONVERSATION"));
    ChatHistory h = new ChatHistory("sess-1", msgs);
    assertEquals(2, h.messages().size());
    assertEquals("assistant", h.messages().get(1).role());
    assertEquals("CONVERSATION", h.messages().get(1).agentType());
  }
}

class AssessmentRequestTest {

  @Test
  void create() {
    AssessmentRequest r = new AssessmentRequest("content-1", "user-1", 5, "medium");
    assertEquals("content-1", r.contentId());
    assertEquals("user-1", r.userId());
    assertEquals(5, r.questionCount());
    assertEquals("medium", r.difficulty());
  }
}

class InsightReportTest {

  @Test
  void create() {
    InsightReport r = new InsightReport("user-1", "Excellent progress", "math", List.of("topic1"));
    assertEquals("user-1", r.userId());
    assertEquals("Excellent progress", r.summary());
    assertEquals("math", r.focusArea());
    assertEquals(1, r.recommendations().size());
  }
}
