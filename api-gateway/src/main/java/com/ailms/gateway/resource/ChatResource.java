package com.ailms.gateway.resource;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.gateway.service.OrchestratorClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/api/v1/chat")
@Tag(name = "Chat", description = "Conversation endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

  @Inject @RestClient OrchestratorClient orchestrator;

  @POST
  public Response sendMessage(ChatRequest request) {
    ChatResponse response = orchestrator.processMessage(request);
    return Response.ok(response).build();
  }

  @GET
  @Path("/history/{sessionId}")
  public Response getHistory(@PathParam("sessionId") String sessionId) {
    return Response.ok().build();
  }
}
