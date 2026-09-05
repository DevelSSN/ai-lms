package com.ailms.orchestrator.resource;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.ThreadRenameRequest;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.service.OrchestratorService;
import com.ailms.orchestrator.service.SessionOwnershipException;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Slf4j
@Path("/api/v1/orchestrate")
@Tag(name = "Orchestrator", description = "LLM Orchestrator endpoints")
@jakarta.ws.rs.Produces(MediaType.APPLICATION_JSON)
@jakarta.ws.rs.Consumes(MediaType.APPLICATION_JSON)
public class OrchestratorResource {

  @Inject OrchestratorService orchestratorService;

  @Inject ConversationRepository conversationRepository;

  @POST
  public Response processMessage(ChatRequest request, @HeaderParam("X-User-Id") String userId) {
    log.info("Orchestrate request from user={} session={}", userId, request.sessionId());
    try {
      ChatResponse response = orchestratorService.route(request, userId);
      log.debug("Orchestrate response sent to user={}", userId);
      return Response.ok(response).build();
    } catch (SessionOwnershipException e) {
      log.warn("Blocked cross-user session access: {}", e.getMessage());
      return Response.status(Response.Status.FORBIDDEN)
          .entity(Map.of("error", "Session does not belong to the authenticated user"))
          .build();
    }
  }

  @GET
  @Path("/history/{sessionId}")
  public Response getHistory(
      @PathParam("sessionId") String sessionId, @HeaderParam("X-User-Id") String userId) {
    log.info("History request for user={} session={}", userId, sessionId);
    ChatHistory history = conversationRepository.getHistory(userId, sessionId);
    return Response.ok(history).build();
  }

  @GET
  @Path("/threads")
  public Response getThreads(@HeaderParam("X-User-Id") String userId) {
    log.info("Thread list request for user={}", userId);
    return Response.ok(conversationRepository.listThreads(userId)).build();
  }

  @PATCH
  @Path("/threads/{sessionId}")
  public Response renameThread(
      @PathParam("sessionId") String sessionId,
      ThreadRenameRequest request,
      @HeaderParam("X-User-Id") String userId) {
    log.info("Rename thread session={} user={}", sessionId, userId);
    conversationRepository.renameThread(userId, sessionId, request.title());
    return Response.noContent().build();
  }

  @DELETE
  @Path("/threads/{sessionId}")
  public Response deleteThread(
      @PathParam("sessionId") String sessionId, @HeaderParam("X-User-Id") String userId) {
    log.info("Delete thread session={} user={}", sessionId, userId);
    conversationRepository.deleteThread(userId, sessionId);
    return Response.noContent().build();
  }
}
