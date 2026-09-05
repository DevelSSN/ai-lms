package com.ailms.orchestrator.service;

import com.ailms.common.constants.PromptPrefixes;
import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ThreadSummary;
import com.ailms.common.entity.UserProfile;
import com.ailms.common.enums.ChatRole;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class InsightDataService {

  private static final DateTimeFormatter TS =
      DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

  @Inject ConversationRepository conversationRepository;

  @Inject UserProfileRepository userProfileRepository;

  public String buildContext(String userId, String sessionId) {
    if (userId == null) return null;

    StringBuilder sb = new StringBuilder("LEARNER ANALYTICS DATA");
    sb.append('\n');

    UserProfile profile = userProfileRepository.findByExternalId(userId);
    if (profile != null) {
      appendIfPresent(sb, "Knowledge level", profile.knowledgeLevel);
      appendIfPresent(sb, "Interests", profile.interests);
      appendIfPresent(sb, "Recorded learning traits", profile.behavioralTraits);
    }

    long userMessages = 0;
    long assistantMessages = 0;
    long uploads = 0;
    Instant first = null;
    Instant last = null;
    try {
      ChatHistory history = conversationRepository.getHistory(userId, sessionId);
      if (history != null && history.messages() != null) {
        for (ChatHistory.ChatMessage msg : history.messages()) {
          if (msg == null || msg.content() == null) continue;
          if (ChatRole.isUser(msg.role())) {
            userMessages++;
            if (msg.content().startsWith(PromptPrefixes.UPLOAD_ANALYSIS)) uploads++;
          } else if (ChatRole.isAssistant(msg.role())) {
            assistantMessages++;
          }
          if (msg.timestamp() == null) continue;
          if (first == null || msg.timestamp().isBefore(first)) first = msg.timestamp();
          if (last == null || msg.timestamp().isAfter(last)) last = msg.timestamp();
        }
      }
    } catch (Exception e) {
      log.warn("Failed to read session history for insights user={}: {}", userId, e.getMessage());
    }

    sb.append("- Messages in this session: ").append(userMessages + assistantMessages).append('\n');
    sb.append("  - Student messages: ").append(userMessages).append('\n');
    sb.append("  - Assistant messages: ").append(assistantMessages).append('\n');
    if (first != null && last != null) {
      sb.append("- Session start: ").append(TS.format(first)).append('\n');
      sb.append("- Session last activity: ").append(TS.format(last)).append('\n');
    }
    if (uploads > 0) {
      sb.append("- Files analyzed in this session: ").append(uploads).append('\n');
    }

    try {
      List<ThreadSummary> threads = conversationRepository.listThreads(userId);
      if (threads != null) {
        long totalMessages = 0;
        long activeThreads = 0;
        for (ThreadSummary t : threads) {
          totalMessages += t.messageCount();
          if (t.lastActive() != null) activeThreads++;
        }
        sb.append("- Total conversation threads: ").append(threads.size()).append('\n');
        sb.append("- Threads with activity: ").append(activeThreads).append('\n');
        sb.append("- Total messages across threads: ").append(totalMessages).append('\n');
      }
    } catch (Exception e) {
      log.warn("Failed to read thread summary for insights user={}: {}", userId, e.getMessage());
    }

    return sb.toString();
  }

  private void appendIfPresent(StringBuilder sb, String label, String value) {
    if (value == null || value.isBlank()) return;
    sb.append("- ").append(label).append(": ").append(value.trim()).append('\n');
  }
}
