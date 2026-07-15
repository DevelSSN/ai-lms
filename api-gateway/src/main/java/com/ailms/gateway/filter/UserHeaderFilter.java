package com.ailms.gateway.filter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Slf4j
@Provider
@Priority(Priorities.AUTHENTICATION)
public class UserHeaderFilter implements ClientRequestFilter {

    @Inject
    JsonWebToken jwt;

    @Override
    public void filter(ClientRequestContext ctx) {
        if (jwt != null && jwt.getSubject() != null) {
            ctx.getHeaders().putSingle("X-User-Id", jwt.getSubject());
            log.debug("Injected X-User-Id={} for {}", jwt.getSubject(), ctx.getUri().getPath());
        }
    }
}
