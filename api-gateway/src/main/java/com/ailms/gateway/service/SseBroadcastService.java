package com.ailms.gateway.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@ApplicationScoped
public class SseBroadcastService {

  private final CopyOnWriteArrayList<MultiEmitter<? super String>> emitters = new CopyOnWriteArrayList<>();

  public Multi<String> subscribe() {
    log.info("New SSE subscriber connected (total: {})", emitters.size() + 1);
    return Multi.createFrom().emitter(em -> {
      emitters.add(em);
      em.onTermination(() -> {
        emitters.remove(em);
        log.info("SSE subscriber disconnected (total: {})", emitters.size());
      });
    });
  }

  public void broadcast(String userId, String message) {
    if (emitters.isEmpty()) {
      log.warn("SSE broadcast skipped — no active subscribers");
      return;
    }
    String payload = "{\"user_id\":\"" + userId + "\",\"response\":\"" + escape(message) + "\"}";
    for (MultiEmitter<? super String> emitter : emitters) {
      try {
        emitter.emit(payload);
      } catch (Exception e) {
        log.error("SSE broadcast failed for user={}: {}", userId, e.getMessage());
        emitters.remove(emitter);
      }
    }
    log.debug("SSE broadcast sent to {} subscribers for user={}", emitters.size(), userId);
  }

  private String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}
