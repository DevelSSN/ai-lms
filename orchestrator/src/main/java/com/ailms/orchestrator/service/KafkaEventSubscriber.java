package com.ailms.orchestrator.service;

import com.ailms.orchestrator.agent.ProactiveAgent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@Slf4j
@ApplicationScoped
public class KafkaEventSubscriber {

  @Inject OrchestratorService orchestratorService;

  @Incoming("proactive-events")
  @Outgoing("proactive-followups")
  public ProactiveAgent.ProactiveFollowUp handleProactiveEvent(ProactiveEvent event) {
    log.info("Received proactive event for user={}", event.userId());

    String message = orchestratorService.generateProactiveMessage(event.userId(), event.context());

    return new ProactiveAgent.ProactiveFollowUp(event.userId(), message);
  }

  public record ProactiveEvent(String userId, String context, String eventType) {}
}
