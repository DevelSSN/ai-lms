package com.ailms.gateway.resource;

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
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Slf4j
@Path("/api/v1/content")
@Tag(name = "Content", description = "Content analysis and assessment endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContentResource {

  @Inject JsonWebToken jwt;

  @POST
  @Path("/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadFile(@RestForm("file") FileUpload file) {
    String userId = jwt.getSubject();
    log.info("File upload from user={} fileName={}", userId, file.fileName());
    return Response.ok().build();
  }

  @POST
  @Path("/assess")
  public Response generateAssessment(com.ailms.common.dto.AssessmentRequest request) {
    log.info("Assessment generation requested");
    return Response.ok().build();
  }

  @GET
  @Path("/insights")
  public Response getInsights() {
    String userId = jwt.getSubject();
    log.info("Insights requested by user={}", userId);
    return Response.ok().build();
  }
}
