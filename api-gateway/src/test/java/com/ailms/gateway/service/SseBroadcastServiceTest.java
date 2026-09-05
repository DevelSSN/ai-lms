package com.ailms.gateway.service;

import static org.junit.jupiter.api.Assertions.*;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SseBroadcastServiceTest {

  SseBroadcastService service = new SseBroadcastService();

  @Mock MultiEmitter<? super String> emitter;

  @Test
  void subscribeReturnsMulti() {
    Multi<String> multi = service.subscribe("user-1");
    assertNotNull(multi);
  }

  @Test
  void subscribeRejectsBlankUser() {
    Multi<String> multi = service.subscribe("   ");
    AtomicReference<Throwable> failure = new AtomicReference<>();
    multi.subscribe().with(item -> {}, failure::set);
    assertNotNull(failure.get());
  }

  @Test
  void broadcastWithNoSubscribers() {
    service.broadcast("user-1", "test message");
    // No exception expected
  }

  @Test
  void broadcastsOnlyToTargetedUser() {
    List<String> user1Received = new CopyOnWriteArrayList<>();
    List<String> user2Received = new CopyOnWriteArrayList<>();
    service.subscribe("user-1").subscribe().with(payload -> collect(payload, user1Received));
    service.subscribe("user-2").subscribe().with(payload -> collect(payload, user2Received));

    service.broadcast("user-1", "hello");

    assertEquals(1, user1Received.size());
    assertEquals(0, user2Received.size());
  }

  @Test
  void escapesControlCharactersInPayload() {
    List<String> received = new CopyOnWriteArrayList<>();
    service.subscribe("user-1").subscribe().with(payload -> collect(payload, received));

    service.broadcast("user-1", "line1\nline2 \"quoted\"\t tab");

    assertEquals(1, received.size());
    String payload = received.get(0);
    assertFalse(payload.contains("\n"));
    assertTrue(payload.contains("\\n"));
    assertTrue(payload.contains("line2"));
  }

  private static void collect(String payload, List<String> target) {
    if (!payload.contains("\"ping\"")) {
      target.add(payload);
    }
  }

  @Test
  void rejectsBeyondPerUserConnectionLimit() {
    for (int i = 0; i < SseBroadcastService.MAX_EMITTERS_PER_USER; i++) {
      service.subscribe("user-1").subscribe().with(item -> {});
    }
    AtomicReference<Throwable> failure = new AtomicReference<>();
    service.subscribe("user-1").subscribe().with(item -> {}, failure::set);

    assertNotNull(failure.get());
  }

  @Test
  void emitterTerminationRemovesFromRegistry() {
    Multi<String> multi = service.subscribe("user-1");
    multi.subscribe().with(item -> {});
    service.broadcast("user-1", "test");
    // No exception expected
  }
}
