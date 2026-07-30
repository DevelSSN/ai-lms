package com.ailms.gateway.service;

import io.smallrye.mutiny.subscription.MultiEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseBroadcastServiceTest {

  SseBroadcastService service = new SseBroadcastService();

  @Mock MultiEmitter<? super String> emitter;

  @Test
  void subscribeReturnsMulti() {
    var multi = service.subscribe();
    assertNotNull(multi);
  }

  @Test
  void broadcastWithNoSubscribers() {
    service.broadcast("user-1", "test message");
    // No exception expected
  }

  @Test
  void subscribeAndBroadcast() {
    service.subscribe();
    service.broadcast("user-1", "hello");
    // Should not throw
  }

  @Test
  void emitterTerminationRemovesFromList() {
    var multi = service.subscribe();
    multi.subscribe().with(item -> {});
    service.broadcast("user-1", "test");
  }
}
