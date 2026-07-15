package com.ailms.orchestrator.agent;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ProactiveAgent {

  @Scheduled(every = "24h")
  void checkFollowUps() {
    log.info("Running scheduled follow-up check");
  }
}
