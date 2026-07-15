package com.ailms.gateway.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

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
            }
        }
    }
}
