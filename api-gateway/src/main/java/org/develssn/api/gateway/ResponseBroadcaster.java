package org.develssn.api.gateway;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;

@Path("/api/updates")
@ApplicationScoped
public class ResponseBroadcaster {

    private final BroadcastProcessor<String> processor = BroadcastProcessor.create();

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> subscribe() {
        return processor;
    }

    public void broadcast(String message) {
        processor.onNext(message);
    }
}
