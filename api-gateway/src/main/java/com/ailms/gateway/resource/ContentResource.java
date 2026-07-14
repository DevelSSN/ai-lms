package com.ailms.gateway.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/v1/content")
@Tag(name = "Content", description = "Content analysis and assessment endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContentResource {

  @POST
  @Path("/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadFile(@RestForm("file") FileUpload file, @RestForm("userId") String userId) {
    return Response.ok().build();
  }

  @POST
  @Path("/assess")
  public Response generateAssessment(com.ailms.common.dto.AssessmentRequest request) {
    return Response.ok().build();
  }

  @GET
  @Path("/insights/{userId}")
  public Response getInsights(@PathParam("userId") String userId) {
    return Response.ok().build();
  }
}
