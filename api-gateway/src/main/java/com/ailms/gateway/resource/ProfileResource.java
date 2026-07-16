package com.ailms.gateway.resource;

import com.ailms.common.dto.ChatResponse;
import com.ailms.gateway.service.OrchestratorClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@Path("/api/v1/profile")
@Tag(name = "Profile", description = "User profile endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileResource {

  @Inject @RestClient OrchestratorClient orchestrator;

  @Inject JsonWebToken jwt;

  @GET
  public Response getProfile() {
    String userId = jwt.getSubject();
    log.info("Profile requested by user={}", userId);
    try {
      com.ailms.common.dto.ChatRequest request = new com.ailms.common.dto.ChatRequest(
          "Show my learning profile", "profile-" + userId);
      ChatResponse response = orchestrator.processMessage(request);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }

  @PUT
  public Response updateProfile(java.util.Map<String, Object> updates) {
    String userId = jwt.getSubject();
    log.info("Profile update by user={} fields={}", userId, updates.keySet());
    try {
      com.ailms.common.dto.ChatRequest request = new com.ailms.common.dto.ChatRequest(
          "Update my profile: " + updates, "profile-" + userId);
      ChatResponse response = orchestrator.processMessage(request);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }
}
