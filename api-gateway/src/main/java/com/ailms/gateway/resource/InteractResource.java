package com.ailms.gateway.resource;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.gateway.service.OrchestratorClient;
import com.ailms.gateway.service.SseBroadcastService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@Path("/api")
public class InteractResource {

  @Inject @RestClient OrchestratorClient orchestrator;

  @Inject SseBroadcastService sse;

  @Inject JsonWebToken jwt;

  @POST
  @Path("/interact")
  @Authenticated
  @jakarta.ws.rs.Consumes(MediaType.APPLICATION_JSON)
  @jakarta.ws.rs.Produces(MediaType.APPLICATION_JSON)
  public Response interact(Map<String, String> body) {
    String message = body.get("message");
    String threadId = body.get("thread_id");
    String userId = jwt.getSubject();
    if (threadId == null || threadId.isBlank()) {
      threadId = UUID.randomUUID().toString();
      log.info("Generated thread id for user={}: {}", userId, threadId);
    }

    log.info("Interact request from user={} thread={}", userId, threadId);

    try {
      ChatRequest request = new ChatRequest(message, threadId);
      ChatResponse response = orchestrator.processMessage(request);
      log.debug("Interact response sent to user={}", userId);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY)
          .entity(Map.of("error", "Orchestrator unavailable"))
          .build();
    }
  }

  @GET
  @Path("/updates")
  @Authenticated
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public Multi<String> stream() {
    return sse.subscribe(jwt.getSubject());
  }
}
