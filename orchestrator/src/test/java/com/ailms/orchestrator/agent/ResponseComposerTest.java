package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResponseComposerTest {

  ResponseComposer composer = new ResponseComposer();

  @Test
  void composeConversation() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "CONVERSATION");
    scope.writeState("response", "Hello!");

    ChatResponse res = composer.compose(scope, "sess-1");
    assertEquals("Hello!", res.message());
    assertEquals("sess-1", res.sessionId());
    assertEquals("CONVERSATION", res.agentType());
  }

  @Test
  void composeAnalysis() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "CONTENT_ANALYSIS");
    scope.writeState("analysis", "Key topics: math, science");

    ChatResponse res = composer.compose(scope, "sess-1");
    assertEquals("Key topics: math, science", res.message());
    assertEquals("CONTENT_ANALYSIS", res.agentType());
  }

  @Test
  void composeAssessment() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "ASSESSMENT");
    scope.writeState("assessment", "Q1: What is 2+2?");

    ChatResponse res = composer.compose(scope, "sess-1");
    assertEquals("Q1: What is 2+2?", res.message());
    assertEquals("ASSESSMENT", res.agentType());
  }

  @Test
  void composeInsight() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "INSIGHT");
    scope.writeState("insights", "You're improving in algebra");

    ChatResponse res = composer.compose(scope, "sess-1");
    assertEquals("You're improving in algebra", res.message());
    assertEquals("INSIGHT", res.agentType());
  }

  @Test
  void composeFallback() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "UNKNOWN");

    ChatResponse res = composer.compose(scope, "sess-1");
    assertEquals("No response generated", res.message());
  }

  @Test
  void composeEmptyScope() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

    ChatResponse res = composer.compose(scope, "sess-1");
    assertEquals("No response generated", res.message());
  }

  @Test
  void composePreservesMetadata() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "CONVERSATION");
    scope.writeState("response", "test");

    ChatResponse res = composer.compose(scope, "sess-1");
    assertNull(res.metadata());
  }
}
