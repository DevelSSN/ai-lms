package com.ailms.gateway.filter;

import static org.mockito.Mockito.*;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenFromQueryFilterTest {

  TokenFromQueryFilter filter = new TokenFromQueryFilter();

  @Mock ContainerRequestContext ctx;
  @Mock UriInfo uriInfo;

  @Test
  void extractsTokenFromUpdatesPath() {
    MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
    params.putSingle("token", "test-token");

    when(ctx.getUriInfo()).thenReturn(uriInfo);
    when(ctx.getHeaders()).thenReturn(params);
    when(uriInfo.getPath()).thenReturn("/api/updates");
    when(uriInfo.getQueryParameters()).thenReturn(params);

    filter.filter(ctx);

    verify(ctx).getHeaders();
    assert params.getFirst("Authorization").equals("Bearer test-token");
  }

  @Test
  void skipsWhenNoToken() {
    MultivaluedMap<String, String> params = new MultivaluedHashMap<>();

    when(ctx.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath()).thenReturn("/api/updates");
    when(uriInfo.getQueryParameters()).thenReturn(params);

    filter.filter(ctx);

    verify(ctx, never()).getHeaders();
  }

  @Test
  void skipsNonUpdatesPath() {
    when(ctx.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath()).thenReturn("/api/v1/chat");

    filter.filter(ctx);

    verify(uriInfo, never()).getQueryParameters();
  }
}
