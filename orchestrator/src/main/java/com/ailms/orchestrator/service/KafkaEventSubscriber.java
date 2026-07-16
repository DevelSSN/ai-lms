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

  @Incoming("proactive-events")
  public void handleProactiveEvent(ProactiveEvent event) {
    log.info("Received proactive event for user={} type={}", event.userId(), event.eventType());

    String message = orchestratorService.generateProactiveMessage(event.userId(), event.context());

    log.info("Generated follow-up for user={}: {}", event.userId(), message);
  }
}
