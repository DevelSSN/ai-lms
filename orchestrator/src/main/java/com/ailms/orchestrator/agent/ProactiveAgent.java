package com.ailms.orchestrator.agent;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProactiveAgent {

  @Scheduled(every = "24h")
  void checkFollowUps() {}
}
