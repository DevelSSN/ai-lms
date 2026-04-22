package org.develssn.api.gateway;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class ResponseConsumer {

    private static final Logger LOG = Logger.getLogger(ResponseConsumer.class);

    @Inject
    ResponseBroadcaster broadcaster;

    @Incoming("processed-responses")
    public void consume(String message) {
        JsonObject json = new JsonObject(message);
        String userId = json.getString("user_id");
        String response = json.getString("response");

        LOG.infof("Composing response for User[%s]: %s", userId, response);
        
        // Broadcast via SSE to the Frontend
        broadcaster.broadcast(message);
    }
}
