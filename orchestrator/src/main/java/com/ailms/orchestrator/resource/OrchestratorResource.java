package com.ailms.orchestrator.resource;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.service.OrchestratorService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
    ChatResponse response = orchestratorService.route(request, userId);
    log.debug("Orchestrate response sent to user={}", userId);
    return Response.ok(response).build();
  }

  @GET
  @Path("/history/{sessionId}")
  public Response getHistory(@PathParam("sessionId") String sessionId) {
    log.info("History request for session={}", sessionId);
    ChatHistory history = conversationRepository.getHistory(sessionId);
    return Response.ok(history).build();
  }
}
