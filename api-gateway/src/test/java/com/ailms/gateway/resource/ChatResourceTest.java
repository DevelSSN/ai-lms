package com.ailms.gateway.resource;

import com.ailms.common.dto.ChatRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatResourceTest {

  @Test
  void testChatRequestValidation() {
    ChatRequest req = new ChatRequest("test message", "session-1");
    assertNotNull(req.message());
    assertNotNull(req.sessionId());
  }
}
