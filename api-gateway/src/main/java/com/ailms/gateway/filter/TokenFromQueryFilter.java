package com.ailms.gateway.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
@Priority(1900)
public class TokenFromQueryFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext ctx) {
    UriInfo uriInfo = ctx.getUriInfo();
    if (uriInfo.getPath().contains("/updates")) {
      MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
      String token = queryParams.getFirst("token");
      if (token != null && !token.isEmpty()) {
        ctx.getHeaders().putSingle("Authorization", "Bearer " + token);
        log.debug("Extracted auth token from query string for /updates");
      } else {
        log.warn("No token found in query string for /updates");
      }
    }
  }
}
