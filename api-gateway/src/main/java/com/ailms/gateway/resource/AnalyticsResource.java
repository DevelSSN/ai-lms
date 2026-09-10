package com.ailms.gateway.resource;

import com.ailms.common.dto.ClassAnalytics;
import com.ailms.common.dto.StudentAnalytics;
import com.ailms.gateway.service.OrchestratorClient;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@Path("/api/v1/analytics")
@Tag(name = "Analytics", description = "Learning analytics endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"STUDENT", "TEACHER", "ADMIN"})
public class AnalyticsResource {

  @Inject @RestClient OrchestratorClient orchestrator;

  @Inject JsonWebToken jwt;

  @GET
  @Path("/student/{studentId}")
  public Response studentAnalytics(@PathParam("studentId") String studentId) {
    String caller = jwt.getSubject();
    Set<String> roles = jwt.getGroups();
    if (!canViewStudent(roles, caller, studentId)) {
      log.warn("RBAC blocked analytics for student={} by user={} roles={}", studentId, caller, roles);
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    try {
      StudentAnalytics analytics = orchestrator.getStudentAnalytics(studentId, caller);
      return Response.ok(analytics).build();
    } catch (Exception e) {
      log.error("Analytics unavailable for student={}: {}", studentId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }

  static boolean canViewStudent(Set<String> roles, String caller, String targetStudentId) {
    if (roles == null || caller == null) return false;
    if (roles.contains("TEACHER") || roles.contains("ADMIN")) return true;
    return caller.equals(targetStudentId);
  }

  @GET
  @Path("/class")
  @RolesAllowed({"TEACHER", "ADMIN"})
  public Response classAnalytics() {
    String caller = jwt.getSubject();
    try {
      ClassAnalytics analytics = orchestrator.getClassAnalytics(caller);
      return Response.ok(analytics).build();
    } catch (Exception e) {
      log.error("Class analytics unavailable: {}", e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }
}