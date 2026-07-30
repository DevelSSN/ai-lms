package com.ailms.gateway.filter;

import jakarta.ws.rs.client.ClientRequestContext;
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

    UserHeaderFilter filter = new UserHeaderFilter();
    filter.jwt = jwt;
    filter.filter(ctx);

    verify(ctx.getHeaders()).putSingle("X-User-Id", "user-1");
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
