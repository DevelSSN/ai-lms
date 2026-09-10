package com.ailms.gateway.resource;

import com.ailms.gateway.service.TeacherStudentService;
import com.ailms.gateway.service.TeacherStudentService.AddStudentRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Slf4j
@Path("/api/v1/teacher")
@Tag(name = "Teacher", description = "Teacher-student association management")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"TEACHER", "ADMIN"})
public class TeacherResource {

  @Inject JsonWebToken jwt;

  @Inject TeacherStudentService service;

  @GET
  @Path("/students")
  public Response myStudents() {
    String teacherId = jwt.getSubject();
    log.info("Teacher students requested by teacher={}", teacherId);
    return Response.ok(service.getStudents(teacherId)).build();
  }

  @POST
  @Path("/students")
  public Response addStudent(AddStudentRequest request) {
    String teacherId = jwt.getSubject();
    try {
      service.addAssociation(teacherId, request.studentId());
      return Response.noContent().build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(java.util.Map.of("error", e.getMessage()))
          .build();
    }
  }

  @DELETE
  @Path("/students/{studentId}")
  public Response removeStudent(@PathParam("studentId") String studentId) {
    if (studentId == null || studentId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    service.removeAssociation(jwt.getSubject(), studentId);
    return Response.noContent().build();
  }
}