package com.ailms.orchestrator.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ailms.orchestrator.repository.ConversationRepository;
import java.util.List;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    com.ailms.common.entity.ConversationLog log = new com.ailms.common.entity.ConversationLog();
    log.role = "user";
    log.message = "hello";
    when(conversationRepository.findRecentByUserId("user-1", 5)).thenReturn(List.of(log));
    when(conversationAgent.process(anyString(), anyString())).thenReturn("Follow up message");

    ProactiveAgent agent = new ProactiveAgent();
    agent.conversationRepository = conversationRepository;
    agent.conversationAgent = conversationAgent;
    agent.eventEmitter = eventEmitter;

    agent.checkFollowUps();
    verify(eventEmitter).send(any(ProactiveAgent.ProactiveEvent.class));
  }
}
