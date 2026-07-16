package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@RegisterAiService
public interface QuestionGenerationPipeline {

  @LoopAgent(
      outputKey = "assessment",
      subAgents = {QuestionGenerationAgent.class, QualityEvaluator.class},
      maxIterations = 3)
  @UserMessage("""
      Generate and refine assessment questions until quality threshold is met:
      {{message}}
      """)
  String generateUntilQuality(@MemoryId String sessionId, @V("message") String message);

  @RegisterAiService
  interface QualityEvaluator {

    @SystemMessage("""
        You are an assessment quality evaluator.
        Rate the quality of the generated questions on a scale of 0-100.
        Consider: relevance, clarity, difficulty balance, and educational value.
        Return ONLY the numeric score.
        """)
    @dev.langchain4j.agentic.Agent(
        name = "QualityEvaluator",
        description = "Evaluates assessment quality and returns score",
        outputKey = "qualityScore")
    @UserMessage("Evaluate this assessment: {{message}}")
    Integer evaluate(@V("message") String assessment);
  }
}
