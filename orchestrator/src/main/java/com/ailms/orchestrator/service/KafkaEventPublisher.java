package com.ailms.orchestrator.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@Slf4j
@ApplicationScoped
public class KafkaEventPublisher {

  @Inject
  @Channel("content-analysis-complete-out")
  Emitter<AgentEvent> contentAnalysisEmitter;

  @Inject
  @Channel("profile-updated-out")
  Emitter<AgentEvent> profileUpdatedEmitter;

  @Inject
  @Channel("insight-generated-out")
  Emitter<AgentEvent> insightGeneratedEmitter;

  public void publishContentAnalysisComplete(String userId, String sessionId, String analysis) {
    AgentEvent event = new AgentEvent(userId, sessionId, analysis, "CONTENT_ANALYSIS");
    contentAnalysisEmitter.send(event);
    log.info("Published content-analysis-complete event for user={}", userId);
  }

  public void publishProfileUpdated(String userId, String sessionId, String profileUpdate) {
    AgentEvent event = new AgentEvent(userId, sessionId, profileUpdate, "PROFILE_UPDATE");
    profileUpdatedEmitter.send(event);
    log.info("Published profile-updated event for user={}", userId);
  }

  public void publishInsightGenerated(String userId, String sessionId, String insight) {
    AgentEvent event = new AgentEvent(userId, sessionId, insight, "INSIGHT");
    insightGeneratedEmitter.send(event);
    log.info("Published insight-generated event for user={}", userId);
  }

  public record AgentEvent(String userId, String sessionId, String data, String eventType) {}
}
