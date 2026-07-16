package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.AgenticScope;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RegisterAiService
public interface OrchestratorRouter {

  @ConditionalAgent(
      outputKey = "response",
      subAgents = {
        @SubAgent(type = ConversationAgent.class, outputKey = "response"),
        @SubAgent(type = ContentAnalysisAgent.class, outputKey = "analysis"),
        @SubAgent(type = QuestionGenerationAgent.class, outputKey = "assessment"),
        @SubAgent(type = InsightAgent.class, outputKey = "insights")
      })
  @ErrorHandler(OrchestratorRouter.ErrorHandlers.class)
  @UserMessage("Route to appropriate agent: {{message}}")
  String route(@MemoryId String sessionId, @V("message") String message);

  @ActivationCondition(
      value = ConversationAgent.class,
      description = "Intent is CONVERSATION - general questions, greetings, casual chat")
  static boolean activateConversation(AgenticScope agenticScope) {
    String intent = agenticScope.readState("intent", "CONVERSATION");
    return "CONVERSATION".equals(intent);
  }

  @ActivationCondition(
      value = ContentAnalysisAgent.class,
      description = "Intent is CONTENT_ANALYSIS - analyze, explain, summarize content")
  static boolean activateContentAnalysis(AgenticScope agenticScope) {
    String intent = agenticScope.readState("intent", "CONVERSATION");
    return "CONTENT_ANALYSIS".equals(intent);
  }

  @ActivationCondition(
      value = QuestionGenerationAgent.class,
      description = "Intent is ASSESSMENT - quizzes, tests, practice questions")
  static boolean activateAssessment(AgenticScope agenticScope) {
    String intent = agenticScope.readState("intent", "CONVERSATION");
    return "ASSESSMENT".equals(intent);
  }

  @ActivationCondition(
      value = InsightAgent.class,
      description = "Intent is INSIGHT - progress reports, analytics, recommendations")
  static boolean activateInsight(AgenticScope agenticScope) {
    String intent = agenticScope.readState("intent", "CONVERSATION");
    return "INSIGHT".equals(intent);
  }

  class ErrorHandlers {
    public static dev.langchain4j.agentic.ErrorRecoveryResult handleError(
        dev.langchain4j.agentic.ErrorContext errorContext) {
      log.error(
          "Agent error in {}: {}",
          errorContext.agentName(),
          errorContext.exception().getMessage());

      if (errorContext.exception() instanceof java.util.concurrent.TimeoutException) {
        return dev.langchain4j.agentic.ErrorRecoveryResult.retry();
      }

      errorContext
          .agenticScope()
          .writeState("response", "I encountered an issue processing your request. Please try again.");
      return dev.langchain4j.agentic.ErrorRecoveryResult.proceed();
    }
  }
}
