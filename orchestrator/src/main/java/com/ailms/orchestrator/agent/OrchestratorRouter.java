package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface OrchestratorRouter {

  @ConditionalAgent(
      outputKey = "response",
      subAgents = {
        ConversationAgent.class,
        AnalysisPipeline.class,
        QuestionGenerationPipeline.class,
        InsightAgent.class
      })
  @UserMessage("""
      Route to appropriate agent based on intent.

      Analysis context (if available):
      {{analysisContext}}

      Message:
      {{message}}
      """)
  String route(@MemoryId String sessionId,
               @V("message") String message,
               @V("analysisContext") String analysisContext,
               @V("intent") String intent);

  @ActivationCondition(
      value = ConversationAgent.class,
      description = "Intent is CONVERSATION - general questions, greetings, casual chat")
  static boolean activateConversation(AgenticScope agenticScope) {
    String intent = agenticScope.readState("intent", "CONVERSATION");
    return "CONVERSATION".equals(intent);
  }

  @ActivationCondition(
      value = AnalysisPipeline.class,
      description = "Intent is CONTENT_ANALYSIS - analyze, explain, summarize content")
  static boolean activateContentAnalysis(AgenticScope agenticScope) {
    String intent = agenticScope.readState("intent", "CONVERSATION");
    return "CONTENT_ANALYSIS".equals(intent);
  }

  @ActivationCondition(
      value = QuestionGenerationPipeline.class,
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
}
