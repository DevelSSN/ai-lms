package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.entity.ConversationLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConversationAgent {

  @Inject jakarta.persistence.EntityManager em;

  @Transactional
  public ChatResponse process(ChatRequest request) {
    ConversationLog log = new ConversationLog();
    log.userId = request.userId();
    log.sessionId = request.sessionId();
    log.role = "user";
    log.message = request.message();
    em.persist(log);

    return new ChatResponse(
        "I understand your message. Let me help you with that.", request.sessionId(), "conversation");
  }
}
