package com.ailms.orchestrator.service;

import com.ailms.orchestrator.agent.ProactiveAgent.ProactiveEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class KafkaEventSubscriber {

  @Inject OrchestratorService orchestratorService;

  @Incoming("proactive-events-in")
  public void handleProactiveEvent(ProactiveEvent event) {
    log.info("Received proactive event for user={} type={}", event.userId(), event.eventType());
    String message = orchestratorService.generateProactiveMessage(event.userId(), event.context());
    log.info("Generated follow-up for user={}: {}", event.userId(), message);
  }

  @Incoming("content-analysis-complete-in")
  public void handleContentAnalysisComplete(KafkaEventPublisher.AgentEvent event) {
    log.info("Content analysis complete for user={} session={}", event.userId(), event.sessionId());
    // Profile update and insight generation triggered downstream via profiling pipeline
  }

  @Incoming("profile-updated-in")
  public void handleProfileUpdated(KafkaEventPublisher.AgentEvent event) {
    log.info("Profile updated for user={} session={}", event.userId(), event.sessionId());
    // Could trigger insight generation or recommendation updates
  }

  @Incoming("insight-generated-in")
  public void handleInsightGenerated(KafkaEventPublisher.AgentEvent event) {
    log.info("Insight generated for user={} session={}", event.userId(), event.sessionId());
    // Could trigger notification or dashboard refresh
  }
}
