package com.ailms.gateway.service;

import com.ailms.common.dto.AgentEvent;
import com.ailms.common.dto.ProactiveEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/** Relays orchestrator Kafka events to the owning user's SSE stream. */
@Slf4j
@ApplicationScoped
public class SseEventBridge {

  @Inject SseBroadcastService sse;

  @Incoming("proactive-events")
  public void onProactiveEvent(ProactiveEvent event) {
    log.info("Relaying proactive follow-up to user={} type={}", event.userId(), event.eventType());
    sse.broadcast(event.userId(), event.context());
  }

  @Incoming("content-analysis-complete")
  public void onContentAnalysisComplete(AgentEvent event) {
    log.info("Relaying content analysis to user={}", event.userId());
    sse.broadcast(event.userId(), event.data());
  }

  @Incoming("profile-updated")
  public void onProfileUpdated(AgentEvent event) {
    log.info("Relaying profile update to user={}", event.userId());
    sse.broadcast(event.userId(), event.data());
  }

  @Incoming("insight-generated")
  public void onInsightGenerated(AgentEvent event) {
    log.info("Relaying insight to user={}", event.userId());
    sse.broadcast(event.userId(), event.data());
  }
}
