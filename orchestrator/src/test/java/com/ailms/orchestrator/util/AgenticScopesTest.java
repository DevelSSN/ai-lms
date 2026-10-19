package com.ailms.orchestrator.util;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgenticScopesTest {

  @AfterEach
  void clearContext() {
    LangChain4jManaged.removeCurrent();
  }

  @Test
  void callWithScope_installsScopeDuringCall_andRestoresAfter() {
    AtomicReference<AgenticScope> inside = new AtomicReference<>();
    assertNull(LangChain4jManaged.current(AgenticScope.class));

    String result =
        AgenticScopes.callWithScope(
            () -> {
              inside.set(LangChain4jManaged.current(AgenticScope.class));
              return "done";
            });

    assertEquals("done", result);
    assertNotNull(inside.get());
    assertNull(LangChain4jManaged.current(AgenticScope.class));
  }

  @Test
  void callWithScope_restoresPreviousScopeWhenNested() {
    AgenticScope outer = dev.langchain4j.agentic.scope.DefaultAgenticScope.ephemeralAgenticScope();
    LangChain4jManaged.setCurrent(java.util.Map.of(AgenticScope.class, outer));

    AgenticScope seen =
        AgenticScopes.callWithScope(() -> LangChain4jManaged.current(AgenticScope.class));

    assertSame(outer, seen);
    assertSame(outer, LangChain4jManaged.current(AgenticScope.class));
  }

  @Test
  void callWithScope_propagatesException_andStillClearsContext() {
    assertThrows(
        IllegalStateException.class,
        () ->
            AgenticScopes.callWithScope(
                () -> {
                  throw new IllegalStateException("boom");
                }));
    assertNull(LangChain4jManaged.current(AgenticScope.class));
  }
}
