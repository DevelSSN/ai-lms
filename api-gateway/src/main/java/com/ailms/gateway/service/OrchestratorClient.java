package com.ailms.gateway.service;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.ThreadRenameRequest;
import com.ailms.common.dto.ThreadSummary;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/orchestrate")
@RegisterRestClient(configKey = "orchestrator")
public interface OrchestratorClient {

  @POST
  ChatResponse processMessage(
      ChatRequest request, @HeaderParam("X-User-Id") String userId);

  @GET
  @Path("/history/{sessionId}")
  ChatHistory getHistory(
      @PathParam("sessionId") String sessionId, @HeaderParam("X-User-Id") String userId);

  @GET
  @Path("/threads")
  List<ThreadSummary> getThreads(@HeaderParam("X-User-Id") String userId);

  @PATCH
  @Path("/threads/{sessionId}")
  void renameThread(
      @PathParam("sessionId") String sessionId,
      ThreadRenameRequest request,
      @HeaderParam("X-User-Id") String userId);

  @DELETE
  @Path("/threads/{sessionId}")
  void deleteThread(
      @PathParam("sessionId") String sessionId, @HeaderParam("X-User-Id") String userId);
}
