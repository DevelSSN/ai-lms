package com.ailms.gateway.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
@Priority(1900)
public class TokenFromQueryFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext ctx) {
    UriInfo uriInfo = ctx.getUriInfo();
    if (!isSseEndpoint(uriInfo.getPath())) return;

    MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
    String token = queryParams.getFirst("token");
    if (token != null && !token.isBlank()) {
      ctx.getHeaders().putSingle("Authorization", "Bearer " + token);
      log.debug("Extracted auth token from query string for SSE endpoint");
      return;
    }

    if (ctx.getHeaders().getFirst("Authorization") != null) {
      log.debug("SSE endpoint authenticated via Authorization header");
      return;
    }

    log.warn("Rejecting SSE request without token (path={})", uriInfo.getPath());
    ctx.abortWith(
        Response.status(Response.Status.UNAUTHORIZED)
            .entity(Map.of("error", "Missing token query parameter for SSE connection"))
            .build());
  }

  private static boolean isSseEndpoint(String path) {
    String normalized = path.startsWith("/") ? path.substring(1) : path;
    return "api/updates".equals(normalized) || normalized.startsWith("api/updates/");
  }
}