package com.ailms.gateway.filter;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies a bearer token from the {@code ?token=} query parameter into the {@code Authorization}
 * header for the SSE endpoint, before Quarkus OIDC authentication runs.
 *
 * <p>The browser's {@code EventSource} cannot set request headers, so the SSE stream authenticates
 * via the query string. A JAX-RS {@code ContainerRequestFilter} is too late (OIDC authenticates in
 * the Vert.x phase first), so this must be a Vert.x route filter. Quarkus registers the
 * authentication handler as a {@code FilterBuildItem} with priority {@code 200}; Vert.x executes
 * lower route orders first, hence priority {@code 100}.
 */
@Slf4j
@ApplicationScoped
public class SseTokenRouteFilter {

  static final String UPDATES_PATH = "api/updates";
  private static final String AUTHORIZATION = "Authorization";
  // Checked first by io.quarkus.oidc.runtime.OidcUtils.extractBearerToken(RoutingContext, ...)
  // before it falls back to the Authorization header; lets us hand OIDC a token that was
  // not present on the original request.
  private static final String EXTRACTED_BEARER_TOKEN_ATTR = "quarkus.oidc.extracted-bearer-token";
  private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9._\\-]+");

  enum Action {
    PASS,
    SET_AUTH,
    REJECT
  }

  @RouteFilter(100)
  void copyQueryToken(RoutingContext rc) {
    String path = rc.normalizedPath();
    String existingAuth = rc.request().getHeader(AUTHORIZATION);
    String token = rc.request().getParam("token");

    switch (resolve(path, existingAuth, token)) {
      case PASS -> rc.next();
      case SET_AUTH -> {
        rc.request().headers().set(AUTHORIZATION, "Bearer " + token);
        rc.put(EXTRACTED_BEARER_TOKEN_ATTR, token);
        log.debug("Extracted auth token from query string for path={}", path);
        rc.next();
      }
      case REJECT -> {
        log.warn("Rejecting SSE request: missing or malformed token (path={})", path);
        rc.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end("{\"error\":\"Missing or invalid token query parameter for SSE connection\"}");
      }
    }
  }

  static Action resolve(String path, String existingAuth, String token) {
    if (!isSseEndpoint(path)) return Action.PASS;
    if (existingAuth != null && !existingAuth.isBlank()) return Action.PASS;
    if (token == null || token.isBlank()) return Action.REJECT;
    if (!SAFE_TOKEN.matcher(token).matches()) return Action.REJECT;
    return Action.SET_AUTH;
  }

  private static boolean isSseEndpoint(String path) {
    String normalized = path.startsWith("/") ? path.substring(1) : path;
    return UPDATES_PATH.equals(normalized) || normalized.startsWith(UPDATES_PATH + "/");
  }
}
