package com.ailms.orchestrator.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.ailms.common.entity.UserProfile;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import java.time.Duration;
import java.time.Instant;
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
  @Mock Emitter<ProactiveAgent.ProactiveEvent> eventEmitter;

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

    newAgent().checkFollowUps();
    verify(eventEmitter).send(any(ProactiveAgent.ProactiveEvent.class));
    verify(userProfileRepository).markProactiveSent(eq("user-1"), any(Instant.class));
  }

  @Test
  void checkFollowUps_withRecentActivity() {
    when(conversationRepository.findInactiveUsersSince(any())).thenReturn(List.of("user-1"));
    com.ailms.common.entity.ConversationLog log = new com.ailms.common.entity.ConversationLog();
    log.role = "user";
    log.message = "hello";
    when(conversationRepository.findRecentByUserId("user-1", 5)).thenReturn(List.of(log));
    when(proactiveFollowUpAgent.generate(anyString())).thenReturn("Follow up message");

    newAgent().checkFollowUps();
    verify(eventEmitter).send(any(ProactiveAgent.ProactiveEvent.class));
  }

  @Test
  void checkFollowUps_skipsRecentlyPingedUser() {
    when(conversationRepository.findInactiveUsersSince(any())).thenReturn(List.of("user-1"));
    UserProfile profile = new UserProfile();
    profile.lastProactiveSentAt = Instant.now();
    when(userProfileRepository.findByExternalId("user-1")).thenReturn(profile);

    newAgent().checkFollowUps();
    verifyNoInteractions(eventEmitter);
    verify(userProfileRepository, never()).markProactiveSent(anyString(), any());
  }
}