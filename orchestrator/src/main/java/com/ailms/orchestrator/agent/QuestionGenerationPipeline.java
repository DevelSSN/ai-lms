package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.AgenticScope;
import dev.langchain4j.agentic.declarative.ExitCondition;
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
  @ExitCondition(QuestionGenerationPipeline.ExitConditions.class)
  @UserMessage("""
      Generate and refine assessment questions until quality threshold is met:
      {{content}}
      """)
  String generateUntilQuality(@MemoryId String sessionId, @V("content") String content);

  class ExitConditions {
    public static boolean shouldExit(AgenticScope agenticScope) {
      Integer qualityScore = agenticScope.readState("qualityScore", 0);
      return qualityScore >= 80;
    }
  }

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
    @UserMessage("Evaluate this assessment: {{assessment}}")
    Integer evaluate(@V("assessment") String assessment);
  }
}
