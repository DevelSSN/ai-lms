package com.ailms.orchestrator.agent;

import com.ailms.orchestrator.repository.ConversationRepository;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProactiveAgentTest {

  @Mock ConversationRepository conversationRepository;
  @Mock ConversationAgent conversationAgent;
  @Mock Emitter<ProactiveAgent.ProactiveEvent> eventEmitter;

  @Test
  void checkFollowUps_noInactiveUsers() {
    when(conversationRepository.findInactiveUsersSince(any())).thenReturn(List.of());

    ProactiveAgent agent = new ProactiveAgent();
    agent.conversationRepository = conversationRepository;

    agent.checkFollowUps();
    verifyNoInteractions(eventEmitter);
  }

  @Test
  void checkFollowUps_withInactiveUser() {
    when(conversationRepository.findInactiveUsersSince(any())).thenReturn(List.of("user-1"));
    when(conversationRepository.findRecentByUserId("user-1", 5)).thenReturn(List.of());
    when(conversationAgent.process(anyString(), anyString())).thenReturn("Follow up message");

    ProactiveAgent agent = new ProactiveAgent();
    agent.conversationRepository = conversationRepository;
    agent.conversationAgent = conversationAgent;
    agent.eventEmitter = eventEmitter;

    agent.checkFollowUps();
    verify(eventEmitter).send(any(ProactiveAgent.ProactiveEvent.class));
  }

  @Test
  void checkFollowUps_withRecentActivity() {
    when(conversationRepository.findInactiveUsersSince(any())).thenReturn(List.of("user-1"));

    ProactiveAgent agent = new ProactiveAgent();
    agent.conversationRepository = conversationRepository;
    agent.eventEmitter = eventEmitter;

    agent.checkFollowUps();
  }
}
