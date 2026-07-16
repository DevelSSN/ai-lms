package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.OrchestratorRouter;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.repository.ConversationRepository;
import dev.langchain4j.agentic.AgenticScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class OrchestratorService {

  @Inject IntentClassifier intentClassifier;

  @Inject OrchestratorRouter orchstratorRouter;

  @Inject ResponseComposer responseComposer;

  @Inject ConversationAgent conversationAgent;

  @Inject ProfilingService profilingService;

  @Inject ConversationRepository conversationRepository;

  public ChatResponse route(ChatRequest request, String userId) {
    profilingService.ensureProfile(userId);

    String intent = intentClassifier.classify(request.message());
    log.info("Intent={} for user={} message={}", intent, userId, request.message());

    AgenticScope scope = AgenticScope.create();
    scope.writeState("intent", intent);

    String agentResponse = orchstratorRouter.route(request.sessionId(), request.message());

    scope.writeState("response", agentResponse);
    ChatResponse response = responseComposer.compose(scope, request.sessionId());

    conversationRepository.logMessage(userId, request.sessionId(), "user", request.message());
    conversationRepository.logMessage(
        userId, request.sessionId(), "assistant", response.message(), response.agentType());

    log.info("Response ready for user={} type={}", userId, intent);
    return response;
  }

  public String generateProactiveMessage(String userId, String context) {
    return conversationAgent.process(
        "proactive-" + userId,
        "Generate a brief encouraging follow-up message for a student who hasn't been active. Context: " + context);
  }
}
