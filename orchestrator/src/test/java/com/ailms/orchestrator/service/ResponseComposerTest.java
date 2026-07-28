package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.agent.ResponseComposer;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResponseComposerTest {

  @Test
  void testComposeConversation() {
    ResponseComposer composer = new ResponseComposer();
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "CONVERSATION");
    scope.writeState("response", "Hello!");

    ChatResponse res = composer.compose(scope, "sess-1");
    assertEquals("Hello!", res.message());
    assertEquals("sess-1", res.sessionId());
    assertEquals("CONVERSATION", res.agentType());
  }

  @Test
  void testComposeAnalysis() {
    ResponseComposer composer = new ResponseComposer();
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "CONTENT_ANALYSIS");
    scope.writeState("analysis", "Key topics: math, science");

    ChatResponse res = composer.compose(scope, "sess-1");
    assertEquals("Key topics: math, science", res.message());
  }

  @Test
  void testComposeFallback() {
    ResponseComposer composer = new ResponseComposer();
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "UNKNOWN");

    ChatResponse res = composer.compose(scope, "sess-1");
    assertEquals("No response generated", res.message());
  }
}
