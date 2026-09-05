package com.ailms.orchestrator.service;

import com.ailms.common.dto.AgentEvent;
import com.ailms.common.dto.ProactiveEvent;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class KafkaEventSubscriber {

  @Incoming("proactive-events-in")
  public void handleProactiveEvent(ProactiveEvent event) {
    // The follow-up text is already generated; the gateway pushes it to the owning user's SSE stream.
    log.info(
        "Proactive follow-up for user={} type={} relayed for SSE delivery",
        event.userId(),
        event.eventType());
  }

  @Incoming("content-analysis-complete-in")
  public void handleContentAnalysisComplete(AgentEvent event) {
    log.info("Content analysis complete for user={} session={}", event.userId(), event.sessionId());
    // Profile update and insight generation triggered downstream via profiling pipeline
  }

  @Incoming("profile-updated-in")
  public void handleProfileUpdated(AgentEvent event) {
    log.info("Profile updated for user={} session={}", event.userId(), event.sessionId());
    // Could trigger insight generation or recommendation updates
  }

  @Incoming("insight-generated-in")
  public void handleInsightGenerated(AgentEvent event) {
    log.info("Insight generated for user={} session={}", event.userId(), event.sessionId());
    // Could trigger notification or dashboard refresh
  }
}
