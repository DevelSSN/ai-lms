package com.ailms.gateway.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class SseBroadcastService {

  private final CopyOnWriteArrayList<MultiEmitter<? super String>> emitters =
      new CopyOnWriteArrayList<>();

  public Multi<String> subscribe() {
    log.info("New SSE subscriber connected (total: {})", emitters.size() + 1);
    return Multi.createFrom()
        .emitter(
            em -> {
              emitters.add(em);
              AtomicReference<Cancellable> keepaliveRef = new AtomicReference<>();
              Cancellable keepalive =
                  Multi.createFrom()
                      .ticks()
                      .every(Duration.ofSeconds(25))
                      .subscribe()
                      .with(
                          tick -> {
                            if (em.isCancelled()) {
                              keepaliveRef.get().cancel();
                              emitters.remove(em);
                              return;
                            }
                            try {
                              em.emit("{\"type\":\"ping\"}");
                            } catch (Exception e) {
                              emitters.remove(em);
                              keepaliveRef.get().cancel();
                            }
                          });
              keepaliveRef.set(keepalive);
              em.onTermination(
                  () -> {
                    Cancellable ka = keepaliveRef.get();
                    if (ka != null) ka.cancel();
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
