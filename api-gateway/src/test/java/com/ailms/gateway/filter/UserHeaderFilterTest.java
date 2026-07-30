package com.ailms.gateway.filter;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserHeaderFilterTest {

  @Mock JsonWebToken jwt;
  @Mock ClientRequestContext ctx;

  @Test
  void injectsUserHeaderWhenJwtPresent() throws Exception {
    when(jwt.getSubject()).thenReturn("user-1");
    when(ctx.getUri()).thenReturn(new URI("http://localhost/api/test"));
    MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    when(ctx.getHeaders()).thenReturn(headers);

    UserHeaderFilter filter = new UserHeaderFilter();
    filter.jwt = jwt;
    filter.filter(ctx);

    verify(ctx).getHeaders();
    assert headers.getFirst("X-User-Id").equals("user-1");
  }

  @Test
  void skipsWhenJwtNull() throws Exception {
    UserHeaderFilter filter = new UserHeaderFilter();
    filter.jwt = null;
    filter.filter(ctx);

    verify(ctx, never()).getHeaders();
  }

  @Test
  void skipsWhenSubjectNull() throws Exception {
    when(jwt.getSubject()).thenReturn(null);

    UserHeaderFilter filter = new UserHeaderFilter();
    filter.jwt = jwt;
    filter.filter(ctx);

    verify(ctx, never()).getHeaders();
  }
}
