package com.ailms.gateway.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailms.gateway.filter.SseTokenRouteFilter.Action;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

class SseTokenRouteFilterTest {

  SseTokenRouteFilter filter = new SseTokenRouteFilter();

  // --- resolve() decision table ------------------------------------------------

  @Test
  void nonSsePathPassesThrough() {
    assertEquals(Action.PASS, SseTokenRouteFilter.resolve("/api/v1/chat", null, null));
  }

  @Test
  void updatesPathWithQueryTokenSetsAuth() {
    assertEquals(
        Action.SET_AUTH, SseTokenRouteFilter.resolve("/api/updates", null, "abc.def-ghi_123"));
  }

  @Test
  void updatesSubPathWithQueryTokenSetsAuth() {
    assertEquals(Action.SET_AUTH, SseTokenRouteFilter.resolve("/api/updates/foo", null, "abc"));
  }

  @Test
  void existingAuthorizationHeaderWins() {
    assertEquals(Action.PASS, SseTokenRouteFilter.resolve("/api/updates", "Bearer x", "abc"));
  }

  @Test
  void missingTokenRejected() {
    assertEquals(Action.REJECT, SseTokenRouteFilter.resolve("/api/updates", null, null));
  }

  @Test
  void blankTokenRejected() {
    assertEquals(Action.REJECT, SseTokenRouteFilter.resolve("/api/updates", null, "   "));
  }

  @Test
  void malformedTokenRejected() {
    assertEquals(Action.REJECT, SseTokenRouteFilter.resolve("/api/updates", null, "bad token!"));
  }

  // --- copyQueryToken() interaction ---------------------------------------------

  @Test
  void copiesQueryTokenOntoAuthorizationHeader() {
    RoutingContext rc = mock(RoutingContext.class);
    HttpServerRequest req = mock(HttpServerRequest.class);
    MultiMap headers = mock(MultiMap.class);

    when(rc.normalizedPath()).thenReturn("/api/updates");
    when(rc.request()).thenReturn(req);
    when(req.getHeader("Authorization")).thenReturn(null);
    when(req.getParam("token")).thenReturn("abc.def");
    when(req.headers()).thenReturn(headers);

    filter.copyQueryToken(rc);

    verify(headers).set("Authorization", "Bearer abc.def");
    verify(rc).next();
    verify(rc, never()).response();
  }

  @Test
  void rejectsWith401WhenTokenMissing() {
    RoutingContext rc = mock(RoutingContext.class);
    HttpServerRequest req = mock(HttpServerRequest.class);
    HttpServerResponse resp = mock(HttpServerResponse.class);

    when(rc.normalizedPath()).thenReturn("/api/updates");
    when(rc.request()).thenReturn(req);
    when(req.getHeader("Authorization")).thenReturn(null);
    when(req.getParam("token")).thenReturn(null);
    when(rc.response()).thenReturn(resp);
    when(resp.setStatusCode(401)).thenReturn(resp);
    when(resp.putHeader("Content-Type", "application/json")).thenReturn(resp);

    filter.copyQueryToken(rc);

    verify(resp).end(anyString());
    verify(rc, never()).next();
  }
}
