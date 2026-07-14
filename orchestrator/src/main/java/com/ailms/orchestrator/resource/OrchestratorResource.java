package com.ailms.orchestrator.resource;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.service.OrchestratorService;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/orchestrate")
@Tag(name = "Orchestrator", description = "LLM Orchestrator endpoints")
@jakarta.ws.rs.Produces(MediaType.APPLICATION_JSON)
@jakarta.ws.rs.Consumes(MediaType.APPLICATION_JSON)
public class OrchestratorResource {

  @Inject OrchestratorService orchestratorService;

  @POST
  public Response processMessage(ChatRequest request, @HeaderParam("X-User-Id") String userId) {
    ChatResponse response = orchestratorService.route(request, userId);
    return Response.ok(response).build();
  }
}
