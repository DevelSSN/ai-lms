package com.ailms.orchestrator.agent;

import com.ailms.common.constants.EventTypeKeys;
import com.ailms.common.dto.ProactiveEvent;
import com.ailms.common.entity.ConversationLog;
import com.ailms.common.entity.UserProfile;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@Slf4j
@ApplicationScoped
public class ProactiveAgent {

  private static final String GENERIC_FOLLOW_UP =
      "Hi! Ready to continue learning? Let me know if you have any questions.";

  @Inject ConversationRepository conversationRepository;

  @Inject UserProfileRepository userProfileRepository;

  @Inject ProactiveFollowUpAgent proactiveFollowUpAgent;

  @Inject
  @Channel("proactive-events-out")
  Emitter<ProactiveEvent> eventEmitter;

  @ConfigProperty(name = "ailms.proactive.inactivity-cutoff", defaultValue = "24h")
  Duration inactivityCutoff;

  @Scheduled(every = "5m")
  @Transactional
  void checkFollowUps() {
    log.info("Running scheduled follow-up check");
    Instant cutoff = Instant.now().minus(inactivityCutoff);

    List<String> inactiveUsers = conversationRepository.findInactiveUsersSince(cutoff);

    for (String userId : inactiveUsers) {
      try {
        if (!userProfileRepository.markProactiveSentIfNotRecent(
            userId, Instant.now(), Instant.now().minus(inactivityCutoff))) {
          continue;
        }
        String followUpMessage = generateFollowUp(userId);
        ProactiveEvent event =
            new ProactiveEvent(userId, followUpMessage, EventTypeKeys.FOLLOW_UP);
        eventEmitter.send(event);
        log.info("Sent follow-up event for user={}", userId);
      } catch (Exception e) {
        log.error("Failed to generate follow-up for user={}: {}", userId, e.getMessage());
      }
    }
  }

  private String generateFollowUp(String userId) {
    List<ConversationLog> recentLogs = conversationRepository.findRecentByUserId(userId, 5);

    String context =
        recentLogs.stream()
            .map(log -> log.role + ": " + log.message)
            .reduce("", (a, b) -> a + "\n" + b);

    if (context.isBlank()) {
      return GENERIC_FOLLOW_UP;
    }

    return proactiveFollowUpAgent.generate(context);
  }
}
