package com.ailms.gateway.resource;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.gateway.service.OrchestratorClient;
import com.ailms.gateway.service.SseBroadcastService;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;

@Path("/api")
public class InteractResource {

    @Inject
    @RestClient
    OrchestratorClient orchestrator;

    @Inject
    SseBroadcastService sse;

    @POST
    @Path("/interact")
    @jakarta.ws.rs.Consumes(MediaType.APPLICATION_JSON)
    @jakarta.ws.rs.Produces(MediaType.APPLICATION_JSON)
    public Response interact(Map<String, String> body) {
        String message = body.get("message");
        String userId = body.get("user_id");
        String threadId = body.get("thread_id");

        ChatRequest request = new ChatRequest(message, threadId, userId);
        ChatResponse response = orchestrator.processMessage(request);

        sse.broadcast(userId, response.message());

        return Response.ok().build();
    }

    @GET
    @Path("/updates")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> stream() {
        return sse.subscribe();
    }
}
