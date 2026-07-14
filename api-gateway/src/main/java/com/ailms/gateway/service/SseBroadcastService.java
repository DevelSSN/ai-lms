package com.ailms.gateway.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SseBroadcastService {

    private MultiEmitter<? super String> emitter;

    public Multi<String> subscribe() {
        return Multi.createFrom().emitter(em -> this.emitter = em);
    }

    public void broadcast(String userId, String message) {
        if (emitter != null) {
            emitter.emit("{\"user_id\":\"" + userId + "\",\"response\":\"" + escape(message) + "\"}");
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
