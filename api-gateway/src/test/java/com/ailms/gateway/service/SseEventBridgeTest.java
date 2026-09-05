package com.ailms.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.ailms.common.dto.AgentEvent;
import com.ailms.common.dto.ProactiveEvent;
import com.ailms.gateway.service.ProactiveEventDeserializer;
import com.ailms.gateway.service.SseBroadcastService;
import com.ailms.gateway.service.SseEventBridge;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SseEventBridgeTest {

  @Mock SseBroadcastService sse;

  SseEventBridge bridge;

  @BeforeEach
  void setUp() {
    bridge = new SseEventBridge();
    bridge.sse = sse;
  }

  @Test
  void onProactiveEvent_broadcastsFollowUpToOwningUser() {
    bridge.onProactiveEvent(new ProactiveEvent("user-1", "Time to review!", "FOLLOW_UP"));
    verify(sse).broadcast("user-1", "Time to review!");
  }

  @Test
  void onContentAnalysisComplete_broadcastToOwningUser() {
    bridge.onContentAnalysisComplete(
        new AgentEvent("user-1", "sess-1", "analysis data", "CONTENT_ANALYSIS"));
    verify(sse).broadcast("user-1", "analysis data");
  }

  @Test
  void onProfileUpdated_broadcastToOwningUser() {
    bridge.onProfileUpdated(new AgentEvent("user-1", "sess-1", "profile data", "PROFILE_UPDATE"));
    verify(sse).broadcast("user-1", "profile data");
  }

  @Test
  void onInsightGenerated_broadcastToOwningUser() {
    bridge.onInsightGenerated(new AgentEvent("user-1", "sess-1", "insight data", "INSIGHT"));
    verify(sse).broadcast("user-1", "insight data");
  }

  @Test
  void proactiveEventDeserializer_readsJsonbSerializedEvents() {
    ProactiveEventDeserializer deserializer = new ProactiveEventDeserializer();
    byte[] json =
        "{\"userId\":\"user-1\",\"context\":\"Hello\",\"eventType\":\"FOLLOW_UP\"}"
            .getBytes(StandardCharsets.UTF_8);

    ProactiveEvent event = deserializer.deserialize("proactive-events", json);

    assertEquals("user-1", event.userId());
    assertEquals("Hello", event.context());
    assertEquals("FOLLOW_UP", event.eventType());
  }
}