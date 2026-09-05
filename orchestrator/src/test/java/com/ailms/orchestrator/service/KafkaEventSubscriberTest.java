package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;

import com.ailms.common.dto.AgentEvent;
import com.ailms.common.dto.ProactiveEvent;
import org.junit.jupiter.api.Test;

class KafkaEventSubscriberTest {

  @Test
  void handleProactiveEvent_relaysForSseDelivery() {
    KafkaEventSubscriber sub = new KafkaEventSubscriber();
    // Delivery happens on the gateway SSE bridge; orchestrator must not throw.
    assertDoesNotThrow(
        () -> sub.handleProactiveEvent(new ProactiveEvent("user-1", "context", "FOLLOW_UP")));
  }

  @Test
  void handleContentAnalysisComplete_logsEvent() {
    KafkaEventSubscriber sub = new KafkaEventSubscriber();
    assertDoesNotThrow(
        () ->
            sub.handleContentAnalysisComplete(
                new AgentEvent("user-1", "sess-1", "data", "CONTENT_ANALYSIS")));
  }

  @Test
  void handleProfileUpdated_logsEvent() {
    KafkaEventSubscriber sub = new KafkaEventSubscriber();
    assertDoesNotThrow(
        () -> sub.handleProfileUpdated(new AgentEvent("user-1", "sess-1", "data", "PROFILE_UPDATE")));
  }

  @Test
  void handleInsightGenerated_logsEvent() {
    KafkaEventSubscriber sub = new KafkaEventSubscriber();
    assertDoesNotThrow(
        () -> sub.handleInsightGenerated(new AgentEvent("user-1", "sess-1", "data", "INSIGHT")));
  }
}