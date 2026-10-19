package com.ailms.orchestrator.util;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs agent calls that happen outside {@code OrchestratorService#route} — background title
 * generation and proactive follow-ups — under an ephemeral {@link AgenticScope}. Without an
 * ambient scope langchain4j logs a WARN per call: "Improper invocation of a standalone agent
 * outside of an agentic system, consider using AiServices instead."
 */
public final class AgenticScopes {

  private AgenticScopes() {}

  /** Executes {@code call} with an AgenticScope active, restoring the previous context after. */
  public static <T> T callWithScope(Supplier<T> call) {
    Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> previous =
        LangChain4jManaged.current();
    if (previous != null && previous.get(AgenticScope.class) != null) {
      return call.get();
    }
    LangChain4jManaged.setCurrent(
        Map.of(AgenticScope.class, DefaultAgenticScope.ephemeralAgenticScope()));
    try {
      return call.get();
    } finally {
      if (previous == null || previous.isEmpty()) {
        LangChain4jManaged.removeCurrent();
      } else {
        LangChain4jManaged.setCurrent(previous);
      }
    }
  }
}
