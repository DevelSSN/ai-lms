package com.ailms.orchestrator.agent;

import com.ailms.common.entity.ConversationLog;
import com.ailms.orchestrator.repository.ConversationRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@ApplicationScoped
public class ProactiveAgent {

  @Inject ConversationRepository conversationRepository;

  @Inject ConversationAgent conversationAgent;

  @Channel("proactive-events")
  Emitter<ProactiveEvent> eventEmitter;

  @Scheduled(every = "24h")
  @Transactional
  void checkFollowUps() {
    log.info("Running scheduled follow-up check");
    Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);

    List<String> inactiveUsers =
        conversationRepository.findInactiveUsersSince(cutoff);

    for (String userId : inactiveUsers) {
      try {
        String followUpMessage = generateFollowUp(userId);
        ProactiveEvent event = new ProactiveEvent(userId, followUpMessage, "FOLLOW_UP");
        eventEmitter.send(event);
        log.info("Sent follow-up event for user={}", userId);
      } catch (Exception e) {
        log.error("Failed to generate follow-up for user={}: {}", userId, e.getMessage());
      }
    }
  }

  private String generateFollowUp(String userId) {
    List<ConversationLog> recentLogs =
        conversationRepository.findRecentByUserId(userId, 5);

    String context =
        recentLogs.stream()
            .map(log -> log.role + ": " + log.message)
            .reduce("", (a, b) -> a + "\n" + b);

    if (context.isBlank()) {
      return "Hi! Ready to continue learning? Let me know if you have any questions.";
    }

    return conversationAgent.process("proactive-" + userId,
        "Based on recent conversation, generate a brief encouraging follow-up message. Context: " + context);
  }

  public record ProactiveFollowUp(String userId, String message) {}

  public record ProactiveEvent(String userId, String context, String eventType) {}
}
