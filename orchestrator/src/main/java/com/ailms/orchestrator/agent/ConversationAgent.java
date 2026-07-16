package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.entity.ConversationLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ConversationAgent {

  @Inject jakarta.persistence.EntityManager em;

  @Transactional
  public ChatResponse process(ChatRequest request, String userId) {
    log.info("Processing conversation for user={}", userId);
    ConversationLog logEntry = new ConversationLog();
    logEntry.userId = userId;
    logEntry.sessionId = request.sessionId();
    logEntry.role = "user";
    logEntry.message = request.message();
    em.persist(logEntry);
    log.info("Conversation persisted for user={}", userId);

    return new ChatResponse(
        "I understand your message. Let me help you with that.",
        request.sessionId(),
        "conversation");
  }
}
