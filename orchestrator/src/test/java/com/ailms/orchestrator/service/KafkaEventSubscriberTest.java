package com.ailms.orchestrator.service;

import static org.mockito.Mockito.*;

import com.ailms.orchestrator.agent.ProactiveAgent.ProactiveEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaEventSubscriberTest {

  @Mock OrchestratorService orchestratorService;

  @Test
  void handleProactiveEvent_generatesFollowUp() {
    when(orchestratorService.generateProactiveMessage("user-1", "context"))
        .thenReturn("Follow-up message");

    KafkaEventSubscriber sub = new KafkaEventSubscriber();
    sub.orchestratorService = orchestratorService;

    sub.handleProactiveEvent(new ProactiveEvent("user-1", "context", "FOLLOW_UP"));
    verify(orchestratorService).generateProactiveMessage("user-1", "context");
  }

  @Test
  void handleContentAnalysisComplete_logsEvent() {
    KafkaEventSubscriber sub = new KafkaEventSubscriber();
    sub.orchestratorService = orchestratorService;

    sub.handleContentAnalysisComplete(
        new KafkaEventPublisher.AgentEvent("user-1", "sess-1", "data", "CONTENT_ANALYSIS"));
    // No exception expected
  }

  @Test
  void handleProfileUpdated_logsEvent() {
    KafkaEventSubscriber sub = new KafkaEventSubscriber();
    sub.handleProfileUpdated(
        new KafkaEventPublisher.AgentEvent("user-1", "sess-1", "data", "PROFILE_UPDATE"));
  }

  @Test
  void handleInsightGenerated_logsEvent() {
    KafkaEventSubscriber sub = new KafkaEventSubscriber();
    sub.handleInsightGenerated(
        new KafkaEventPublisher.AgentEvent("user-1", "sess-1", "data", "INSIGHT"));
  }
}
