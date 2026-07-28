package com.ailms.common.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConversationLogTest {

  @Test
  void testCreate() {
    ConversationLog log = new ConversationLog();
    log.userId = "user-1";
    log.sessionId = "sess-1";
    log.role = "user";
    log.message = "hello";
    log.assistantMessage = null;
    log.agentType = null;

    assertEquals("user-1", log.userId);
    assertEquals("sess-1", log.sessionId);
    assertEquals("user", log.role);
    assertEquals("hello", log.message);
  }
}
