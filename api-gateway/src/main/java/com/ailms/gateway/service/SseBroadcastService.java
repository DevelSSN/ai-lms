package com.ailms.gateway.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class SseBroadcastService {

  private volatile MultiEmitter<? super String> emitter;

  public Multi<String> subscribe() {
    log.info("New SSE subscriber connected");
    return Multi.createFrom().emitter(em -> this.emitter = em);
  }

  public void broadcast(String userId, String message) {
    if (emitter != null) {
      try {
        emitter.emit("{\"user_id\":\"" + userId + "\",\"response\":\"" + escape(message) + "\"}");
        log.info("SSE broadcast sent to user={}", userId);
      } catch (Exception e) {
        log.error("SSE broadcast failed for user={}: {}", userId, e.getMessage());
      }
    } else {
      log.warn("SSE broadcast skipped — no active subscriber");
    }
  }

  private String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}
