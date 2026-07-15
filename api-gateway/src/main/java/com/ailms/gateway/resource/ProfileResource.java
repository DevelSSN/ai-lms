package com.ailms.gateway.resource;

import com.ailms.common.entity.UserProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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

@Slf4j
@Path("/api/v1/profile")
@Tag(name = "Profile", description = "User profile endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileResource {

  @Inject EntityManager em;

  @Inject JsonWebToken jwt;

  @GET
  public Response getProfile() {
    String userId = jwt.getSubject();
    log.info("Profile requested by user={}", userId);
    return Response.ok().build();
  }

  @PUT
  public Response updateProfile(UserProfile profile) {
    String userId = jwt.getSubject();
    log.info("Profile update by user={}", userId);
    return Response.ok().build();
  }
}
