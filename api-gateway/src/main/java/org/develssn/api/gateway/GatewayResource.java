package org.develssn.api.gateway;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@Path("/api")
public class GatewayResource {

    @Inject
    @Channel("user-interactions")
    Emitter<String> interactionEmitter;

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        return Response.ok("{\"status\": \"UP\"}").build();
    }

    @POST
    @Path("/interact")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response interact(String body) {
        // Send interaction to Kafka
        interactionEmitter.send(body);
        return Response.ok("{\"status\": \"accepted\", \"message\": \"Interaction queued for processing\"}").build();
    }
}
