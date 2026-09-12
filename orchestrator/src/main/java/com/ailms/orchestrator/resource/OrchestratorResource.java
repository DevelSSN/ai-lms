package com.ailms.orchestrator.resource;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.ClassAnalytics;
import com.ailms.common.dto.QuizResultRequest;
import com.ailms.common.dto.StudentAnalytics;
import com.ailms.common.dto.ThreadRenameRequest;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import com.ailms.orchestrator.service.AnalyticsService;
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

  @Inject AnalyticsService analyticsService;

  @Inject UserProfileRepository userProfileRepository;

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

  @POST
  @Path("/analyze")
  public Response analyze(
      ChatRequest request, @HeaderParam("X-User-Id") String userId) {
    log.info("Async analyze request from user={} session={}", userId, request.sessionId());
    try {
      Map<String, Object> ack = orchestratorService.routeAsync(request, userId);
      return Response.status(Response.Status.ACCEPTED).entity(ack).build();
    } catch (SessionOwnershipException e) {
      log.warn("Blocked cross-user session access: {}", e.getMessage());
      return Response.status(Response.Status.FORBIDDEN)
          .entity(Map.of("error", "Session does not belong to the authenticated user"))
          .build();
    }
  }

  @POST
  @Path("/activity")
  public Response recordActivity(@HeaderParam("X-User-Id") String userId) {
    log.debug("Activity heartbeat for user={}", userId);
    userProfileRepository.recordActivity(userId);
    return Response.ok().build();
  }

  @POST
  @Path("/quiz/results")
  public Response recordQuizResult(
      QuizResultRequest request, @HeaderParam("X-User-Id") String userId) {
    log.info("Quiz result submission from user={} session={}", userId, request.sessionId());
    try {
      orchestratorService.recordQuizResult(userId, request);
      return Response.noContent().build();
    } catch (IllegalArgumentException e) {
      log.warn("Invalid quiz result payload from user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", e.getMessage()))
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

  @GET
  @Path("/analytics/student/{studentId}")
  public Response studentAnalytics(
      @PathParam("studentId") String studentId, @HeaderParam("X-User-Id") String userId) {
    log.info("Student analytics request for student={} by user={}", studentId, userId);
    StudentAnalytics analytics = analyticsService.student(studentId);
    return Response.ok(analytics).build();
  }

  @GET
  @Path("/analytics/class")
  public Response classAnalytics(@HeaderParam("X-User-Id") String userId) {
    log.info("Class analytics request by user={}", userId);
    ClassAnalytics analytics = analyticsService.classAnalytics();
    return Response.ok(analytics).build();
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
