package com.ailms.gateway.service;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/orchestrate")
@RegisterRestClient(configKey = "orchestrator")
public interface OrchestratorClient {

  @POST
  ChatResponse processMessage(ChatRequest request);

  @GET
  @Path("/history/{sessionId}")
  ChatHistory getHistory(@PathParam("sessionId") String sessionId);
}
