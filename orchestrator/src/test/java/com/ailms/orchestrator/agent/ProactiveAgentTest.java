package com.ailms.orchestrator.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import java.time.Duration;
import java.util.List;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProactiveAgentTest {

  @Mock ConversationRepository conversationRepository;
  @Mock UserProfileRepository userProfileRepository;
  @Mock ProactiveFollowUpAgent proactiveFollowUpAgent;
  @Mock Emitter<com.ailms.common.dto.ProactiveEvent> eventEmitter;

  private ProactiveAgent newAgent() {
    ProactiveAgent agent = new ProactiveAgent();
    agent.conversationRepository = conversationRepository;
    agent.userProfileRepository = userProfileRepository;
    agent.proactiveFollowUpAgent = proactiveFollowUpAgent;
    agent.eventEmitter = eventEmitter;
    agent.inactivityCutoff = Duration.ofHours(24);
    return agent;
  }

  @Test
  void checkFollowUps_noInactiveUsers() {
    when(conversationRepository.findInactiveUsersSince(any())).thenReturn(List.of());

    newAgent().checkFollowUps();
    verifyNoInteractions(eventEmitter);
  }

  @Test
  void checkFollowUps_withInactiveUser() {
    when(conversationRepository.findInactiveUsersSince(any())).thenReturn(List.of("user-1"));
    when(conversationRepository.findRecentByUserId("user-1", 5)).thenReturn(List.of());
    when(userProfileRepository.markProactiveSentIfNotRecent(eq("user-1"), any(), any()))
        .thenReturn(true);

    newAgent().checkFollowUps();
    verify(eventEmitter).send(any(com.ailms.common.dto.ProactiveEvent.class));
    verify(userProfileRepository).markProactiveSentIfNotRecent(eq("user-1"), any(), any());
  }

  @Test
  void checkFollowUps_withRecentActivity() {
    when(conversationRepository.findInactiveUsersSince(any())).thenReturn(List.of("user-1"));
    com.ailms.common.entity.ConversationLog log = new com.ailms.common.entity.ConversationLog();
    log.role = "user";
    log.message = "hello";
    when(conversationRepository.findRecentByUserId("user-1", 5)).thenReturn(List.of(log));
    when(proactiveFollowUpAgent.generate(anyString())).thenReturn("Follow up message");
    when(userProfileRepository.markProactiveSentIfNotRecent(eq("user-1"), any(), any()))
        .thenReturn(true);

    newAgent().checkFollowUps();
    verify(eventEmitter).send(any(com.ailms.common.dto.ProactiveEvent.class));
  }

  @Test
  void checkFollowUps_skipsRecentlyPingedUser() {
    when(conversationRepository.findInactiveUsersSince(any())).thenReturn(List.of("user-1"));
    when(userProfileRepository.markProactiveSentIfNotRecent(eq("user-1"), any(), any()))
        .thenReturn(false);

    newAgent().checkFollowUps();
    verifyNoInteractions(eventEmitter);
    verify(userProfileRepository).markProactiveSentIfNotRecent(eq("user-1"), any(), any());
  }
}