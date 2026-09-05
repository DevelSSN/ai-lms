package com.ailms.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class SseBroadcastService {

  public static final int MAX_USERS = 1000;

  public static final int MAX_EMITTERS_PER_USER = 4;

  private static final String PING_PAYLOAD = "{\"type\":\"ping\"}";

  private static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(25);

  private final ConcurrentHashMap<String, CopyOnWriteArrayList<MultiEmitter<? super String>>>
      userEmitters = new ConcurrentHashMap<>();

  private final ObjectMapper objectMapper = new ObjectMapper();

  public Multi<String> subscribe(String userId) {
    if (userId == null || userId.isBlank()) {
      return Multi.createFrom().failure(new IllegalArgumentException("userId must not be blank"));
    }
    CopyOnWriteArrayList<MultiEmitter<? super String>> list =
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
    if (list.size() >= MAX_EMITTERS_PER_USER) {
      log.warn("Rejecting SSE subscription for user={} — connection limit reached", userId);
      return Multi.createFrom()
          .failure(new IllegalStateException("Too many active SSE connections"));
    }
    if (userEmitters.size() > MAX_USERS) {
      log.warn("Rejecting SSE subscription for user={} — registry full", userId);
      return Multi.createFrom().failure(new IllegalStateException("SSE registry is full"));
    }
    log.info("New SSE subscriber for user={} (user connections: {})", userId, list.size() + 1);
    return Multi.createFrom()
        .emitter(
            em -> {
              list.add(em);
              AtomicReference<Cancellable> keepaliveRef = new AtomicReference<>();
              Cancellable keepalive =
                  Multi.createFrom()
                      .ticks()
                      .every(KEEPALIVE_INTERVAL)
                      .subscribe()
                      .with(
                          tick -> {
                            if (em.isCancelled()) {
                              cancel(keepaliveRef);
                              list.remove(em);
                              return;
                            }
                            try {
                              em.emit(PING_PAYLOAD);
                            } catch (Exception e) {
                              list.remove(em);
                              cancel(keepaliveRef);
                            }
                          });
              keepaliveRef.set(keepalive);
              em.onTermination(
                  () -> {
                    cancel(keepaliveRef);
                    list.remove(em);
                    if (list.isEmpty()) {
                      userEmitters.remove(userId, list);
                    }
                    log.info(
                        "SSE subscriber disconnected for user={} (user connections: {})",
                        userId,
                        list.size());
                  });
            });
  }

  public void broadcast(String userId, String message) {
    CopyOnWriteArrayList<MultiEmitter<? super String>> list = userEmitters.get(userId);
    if (list == null || list.isEmpty()) {
      log.warn("SSE broadcast skipped for user={} — no active subscribers", userId);
      return;
    }
    String payload = toPayload(userId, message);
    for (MultiEmitter<? super String> emitter : list) {
      try {
        if (emitter.isCancelled() || emitter.requested() <= 0) {
          list.remove(emitter);
          continue;
        }
        emitter.emit(payload);
      } catch (Exception e) {
        log.error("SSE broadcast failed for user={}: {}", userId, e.getMessage());
        list.remove(emitter);
      }
    }
    log.debug("SSE broadcast sent to {} subscribers for user={}", list.size(), userId);
  }

  private String toPayload(String userId, String message) {
    try {
      return objectMapper.writeValueAsString(
          Map.of("user_id", userId, "response", message == null ? "" : message));
    } catch (Exception e) {
      log.error("Failed to serialize SSE payload, sending empty response", e);
      return "{\"user_id\":\"" + escape(userId) + "\",\"response\":\"\"}";
    }
  }

  private static void cancel(AtomicReference<Cancellable> ref) {
    Cancellable keepalive = ref.get();
    if (keepalive != null) {
      keepalive.cancel();
    }
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
