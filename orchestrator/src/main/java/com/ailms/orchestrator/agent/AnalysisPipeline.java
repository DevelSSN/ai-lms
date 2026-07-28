package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@RegisterAiService
public interface AnalysisPipeline {

  @SequenceAgent(
      outputKey = "analysisResult",
      subAgents = {
        ContentAnalysisAgent.class,
        ProfilingAgent.class,
        InsightAgent.class
      })
  @UserMessage("Analyze content and generate insights: {{message}}")
  String analyze(@MemoryId String sessionId, @V("message") String message);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result("Analysis pipeline encountered an error. Please try again.");
  }
}
