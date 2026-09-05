package com.ailms.orchestrator.service;

import static org.mockito.Mockito.*;

import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

  @Mock Emitter<com.ailms.common.dto.AgentEvent> emitter;

  @Test
  void publishContentAnalysisComplete() {
    KafkaEventPublisher pub = new KafkaEventPublisher();
    pub.contentAnalysisEmitter = emitter;

    pub.publishContentAnalysisComplete("user-1", "sess-1", "analysis result");
    verify(emitter).send(any(com.ailms.common.dto.AgentEvent.class));
  }

  @Test
  void publishProfileUpdated() {
    KafkaEventPublisher pub = new KafkaEventPublisher();
    pub.profileUpdatedEmitter = emitter;

    pub.publishProfileUpdated("user-1", "sess-1", "profile data");
    verify(emitter).send(any(com.ailms.common.dto.AgentEvent.class));
  }

  @Test
  void publishInsightGenerated() {
    KafkaEventPublisher pub = new KafkaEventPublisher();
    pub.insightGeneratedEmitter = emitter;

    pub.publishInsightGenerated("user-1", "sess-1", "insight data");
    verify(emitter).send(any(com.ailms.common.dto.AgentEvent.class));
  }
}
