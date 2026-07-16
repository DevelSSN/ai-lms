package com.ailms.gateway.resource;

import com.ailms.common.dto.AssessmentRequest;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.gateway.service.OrchestratorClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Slf4j
@Path("/api/v1/content")
@Tag(name = "Content", description = "Content analysis and assessment endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContentResource {

  @Inject @RestClient OrchestratorClient orchestrator;

  @Inject JsonWebToken jwt;

  @POST
  @Path("/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadFile(@RestForm("file") FileUpload file) {
    String userId = jwt.getSubject();
    log.info("File upload from user={} fileName={}", userId, file.fileName());
    try {
      ChatRequest request = new ChatRequest("Analyze the uploaded file: " + file.fileName(), "upload-" + userId);
      ChatResponse response = orchestrator.processMessage(request);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }

  @POST
  @Path("/assess")
  public Response generateAssessment(AssessmentRequest request) {
    String userId = jwt.getSubject();
    log.info("Assessment generation requested for content={} by user={}", request.contentId(), userId);
    try {
      ChatRequest chatReq = new ChatRequest(
          "Generate assessment for content " + request.contentId(), "assess-" + userId);
      ChatResponse response = orchestrator.processMessage(chatReq);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }

  @GET
  @Path("/insights")
  public Response getInsights() {
    String userId = jwt.getSubject();
    log.info("Insights requested by user={}", userId);
    try {
      ChatRequest request = new ChatRequest("Show my learning insights and progress", "insight-" + userId);
      ChatResponse response = orchestrator.processMessage(request);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }
}
