package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.OrchestratorRouter;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.repository.ConversationRepository;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
public class OrchestratorService {

  @Inject IntentClassifier intentClassifier;

  @Inject OrchestratorRouter orchestratorRouter;

  @Inject ProfilingAgent profilingAgent;

  @Inject ResponseComposer responseComposer;

  @Inject ConversationAgent conversationAgent;

  @Inject ProfilingService profilingService;

  @Inject ConversationRepository conversationRepository;

  @Inject VectorDBService vectorDBService;

  public ChatResponse route(ChatRequest request, String userId) {
    profilingService.ensureProfile(userId);

    String intent = intentClassifier.classify(request.message());
    log.info("Intent={} for user={} message={}", intent, userId, request.message());

    String enrichedMessage = enrichWithContext(intent, request.message(), userId);

    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", intent);

    String agentResponse = orchestratorRouter.route(request.sessionId(), enrichedMessage);

    profilingAgent.process(request.sessionId(), enrichedMessage);

    scope.writeState("response", agentResponse);
    ChatResponse response = responseComposer.compose(scope, request.sessionId());

    conversationRepository.logMessage(userId, request.sessionId(), "user", request.message());
    conversationRepository.logMessage(
        userId, request.sessionId(), "assistant", response.message(), response.agentType());

    ingestIfContent(intent, request.message(), userId);

    log.info("Response ready for user={} type={}", userId, intent);
    return response;
  }

  private String enrichWithContext(String intent, String message, String userId) {
    if (!"CONTENT_ANALYSIS".equals(intent)) return message;
    try {
      List<String> context = vectorDBService.retrieveRelevantContext(message, 3);
      if (!context.isEmpty()) {
        String ctx = String.join("\n---\n", context);
        return "Relevant context:\n" + ctx + "\n\nContent to analyze: " + message;
      }
    } catch (Exception e) {
      log.warn("Qdrant context retrieval failed for user={}: {}", userId, e.getMessage());
    }
    return message;
  }

  private void ingestIfContent(String intent, String message, String userId) {
    if (!"CONTENT_ANALYSIS".equals(intent)) return;
    try {
      vectorDBService.ingestDocument(message, "user-" + userId, "conversation");
    } catch (Exception e) {
      log.warn("Qdrant ingestion failed for user={}: {}", userId, e.getMessage());
    }
  }

  public String generateProactiveMessage(String userId, String context) {
    return conversationAgent.process(
        "proactive-" + userId,
        "Generate a brief encouraging follow-up message for a student who hasn't been active. Context: " + context);
  }
}
