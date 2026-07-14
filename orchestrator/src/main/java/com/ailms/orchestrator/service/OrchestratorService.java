package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.agent.ContentAnalysisAgent;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.InsightAgent;
import com.ailms.orchestrator.agent.ProactiveAgent;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.QuestionGenerationAgent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrchestratorService {

  @Inject ConversationAgent conversationAgent;

  @Inject ProfilingAgent profilingAgent;

  @Inject ContentAnalysisAgent contentAnalysisAgent;

  @Inject QuestionGenerationAgent questionGenerationAgent;

  @Inject InsightAgent insightAgent;

  @Inject ProactiveAgent proactiveAgent;

  public ChatResponse route(ChatRequest request) {
    profilingAgent.analyze(request);
    String intent = classifyIntent(request.message());

    return switch (intent) {
      case "content_analysis" -> contentAnalysisAgent.process(request);
      case "assessment" -> questionGenerationAgent.process(request);
      case "insight" -> insightAgent.process(request);
      default -> conversationAgent.process(request);
    };
  }

  private String classifyIntent(String message) {
    String msg = message.toLowerCase();
    if (msg.contains("analyze") || msg.contains("explain") || msg.contains("summarize"))
      return "content_analysis";
    if (msg.contains("quiz") || msg.contains("test") || msg.contains("question"))
      return "assessment";
    if (msg.contains("progress") || msg.contains("report") || msg.contains("insight"))
      return "insight";
    return "conversation";
  }
}
