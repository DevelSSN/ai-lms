package com.ailms.gateway.resource;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.ThreadRenameRequest;
import com.ailms.gateway.service.OrchestratorClient;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@Path("/api/v1/chat")
@Tag(name = "Chat", description = "Conversation endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

  @Inject @RestClient OrchestratorClient orchestrator;

  @Inject JsonWebToken jwt;

  @POST
  public Response sendMessage(ChatRequest request) {
    String userId = jwt.getSubject();
    String sessionId = request.sessionId();
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = UUID.randomUUID().toString();
      log.info("Generated session id for user={}: {}", userId, sessionId);
    }
    log.info("Chat request from user={} session={}", userId, sessionId);
    try {
      ChatResponse response =
          orchestrator.processMessage(new ChatRequest(request.message(), sessionId));
      log.debug("Chat response sent to user={}", userId);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/history/{sessionId}")
  public Response getHistory(@PathParam("sessionId") String sessionId) {
    log.info("History request for session={}", sessionId);
    try {
      ChatHistory history = orchestrator.getHistory(sessionId);
      return Response.ok(history).build();
    } catch (Exception e) {
      log.error("Failed to fetch history for session={}: {}", sessionId, e.getMessage());
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/threads")
  public Response getThreads() {
    log.info("Thread list request for user={}", jwt.getSubject());
    try {
      return Response.ok(orchestrator.getThreads()).build();
    } catch (Exception e) {
      log.error("Failed to fetch threads: {}", e.getMessage());
      return Response.serverError().build();
    }
  }

  @PATCH
  @Path("/threads/{sessionId}")
  public Response renameThread(
      @PathParam("sessionId") String sessionId, ThreadRenameRequest request) {
    log.info("Rename thread session={} user={}", sessionId, jwt.getSubject());
    try {
      orchestrator.renameThread(sessionId, request);
      return Response.noContent().build();
    } catch (Exception e) {
      log.error("Failed to rename thread {}: {}", sessionId, e.getMessage());
      return Response.serverError().build();
    }
  }

  @DELETE
  @Path("/threads/{sessionId}")
  public Response deleteThread(@PathParam("sessionId") String sessionId) {
    log.info("Delete thread session={} user={}", sessionId, jwt.getSubject());
    try {
      orchestrator.deleteThread(sessionId);
      return Response.noContent().build();
    } catch (Exception e) {
      log.error("Failed to delete thread {}: {}", sessionId, e.getMessage());
      return Response.serverError().build();
    }
  }
}
